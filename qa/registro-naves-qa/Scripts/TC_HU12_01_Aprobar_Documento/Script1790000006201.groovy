import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.FormDataBodyParameter
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import com.kms.katalon.core.testobject.impl.HttpFormDataBodyContent
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import navesitas.Http
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import com.kms.katalon.core.model.FailureHandling
import static com.kms.katalon.core.testcase.TestCaseFactory.findTestCase
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

String base = 'http://localhost:8080'

/** Inicia sesion y devuelve la cookie JSESSIONID. */
def iniciarSesion = { String correo ->
    RequestObject login = new RequestObject('login')
    login.setRestUrl(base + '/api/auth/login')
    login.setRestRequestMethod('POST')
    login.setHttpHeaderProperties([
        new TestObjectProperty('Content-Type', ConditionType.EQUALS,
            'application/x-www-form-urlencoded')
    ])
    login.setBodyContent(new HttpTextBodyContent(
        'correo=' + correo + '&password=Navesitas2026*'))
    return Http.sesion(Http.enviar(login))
}

/** GET con la cookie de sesion (o sin ella si es null). */
def consultar = { String cookie, String ruta ->
    RequestObject get = new RequestObject('consultar')
    get.setRestUrl(base + ruta)
    get.setRestRequestMethod('GET')
    List<TestObjectProperty> cabeceras = []
    if (cookie) cabeceras << new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
    get.setHttpHeaderProperties(cabeceras)
    return Http.enviar(get)
}

/** POST con cuerpo JSON y la cookie de sesion (o sin ella si es null). */
def postJson = { String cookie, String ruta, String cuerpo ->
    RequestObject p = new RequestObject('post')
    p.setRestUrl(base + ruta)
    p.setRestRequestMethod('POST')
    List<TestObjectProperty> cabeceras = [
        new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json')
    ]
    if (cookie) cabeceras << new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
    p.setHttpHeaderProperties(cabeceras)
    p.setBodyContent(new HttpTextBodyContent(cuerpo))
    return Http.enviar(p)
}

def json = { ResponseObject r -> new JsonSlurper().parseText(r.getResponseBodyContent()) }

/** HU-09: POST /api/naves/{id}/tramite/estado con la accion y, si hay, la observacion. */
def accion = { String cookie, def naveId, String nombre, String observacion = null ->
    Map cuerpo = [accion: nombre]
    if (observacion != null) cuerpo.observacion = observacion
    ResponseObject r = postJson(cookie, '/api/naves/' + naveId + '/tramite/estado', JsonOutput.toJson(cuerpo))
    println(nombre + ' -> ' + r.getStatusCode() + ' ' + r.getResponseBodyContent())
    return r
}

/**
 * HU-12: POST /api/naves/{naveId}/documentos/{documentoId}/revision.
 * Sin observacion (null) el campo no se manda.
 */
def revisar = { String cookie, def naveId, def documentoId, String decision, String observacion = null ->
    Map cuerpo = [decision: decision]
    if (observacion != null) cuerpo.observacion = observacion
    ResponseObject r = postJson(cookie, '/api/naves/' + naveId + '/documentos/' + documentoId + '/revision',
        JsonOutput.toJson(cuerpo))
    println('Revision ' + decision + ' doc ' + documentoId + ' -> ' + r.getStatusCode() + ' ' + r.getResponseBodyContent())
    return r
}

/**
 * HU-08 con la sesion del agente dueno: los documentos de la nave por tipo.
 * Se usa la del agente porque hoy la DGMM recibe 403 en el listado
 * (observacion 2 de la revision de DISENO-V7.md).
 */
def documentosPorTipo = { String cookie, def naveId, boolean historial = false ->
    ResponseObject r = consultar(cookie, '/api/naves/' + naveId + '/documentos' + (historial ? '?historial=true' : ''))
    WS.verifyResponseStatusCode(r, 200)
    return json(r).groupBy { it.tipo }
}

/** Crea un archivo temporal con el contenido dado. */
def crearArchivo = { String nombre, byte[] contenido ->
    File carpeta = new File(System.getProperty('java.io.tmpdir'), 'navesitas-katalon')
    carpeta.mkdirs()
    File f = new File(carpeta, nombre)
    f.bytes = contenido
    f.deleteOnExit()
    return f
}

/** Un PDF minimo valido: lo unico que exige el backend es la firma %PDF-. */
def pdfValido = { String texto ->
    return ('%PDF-1.4\n%' + texto + '\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n').getBytes('UTF-8')
}

/** HU-07: POST multipart con el tipo y el archivo (igual que TS04). */
def cargarDocumento = { String cookie, def naveId, String tipo ->
    File archivo = crearArchivo(tipo.toLowerCase() + '-' + System.nanoTime() + '.pdf', pdfValido(tipo))
    RequestObject carga = new RequestObject('cargarDocumento')
    carga.setRestUrl(base + '/api/naves/' + naveId + '/documentos')
    carga.setRestRequestMethod('POST')
    List<TestObjectProperty> cabeceras = []
    if (cookie) cabeceras << new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
    HttpFormDataBodyContent cuerpo = new HttpFormDataBodyContent([
        new FormDataBodyParameter('tipo', tipo, FormDataBodyParameter.PARAM_TYPE_TEXT),
        new FormDataBodyParameter('archivo', archivo.absolutePath, FormDataBodyParameter.PARAM_TYPE_FILE)
    ])
    cabeceras << new TestObjectProperty('Content-Type', ConditionType.EQUALS, cuerpo.getContentType())
    carga.setHttpHeaderProperties(cabeceras)
    carga.setBodyContent(cuerpo)
    ResponseObject r = Http.enviar(carga)
    println('Carga ' + tipo + ' -> ' + r.getStatusCode())
    return r
}

/** Nave del agente con los 4 documentos; si enRevision, la DGMM la admite. */
def naveConDocumentos = { String dgmm, boolean enRevision ->
    def prep = WebUI.callTestCase(findTestCase('Test Cases/TC_AUX_Nave_Con_Documentos'), [:], FailureHandling.STOP_ON_FAILURE)
    if (enRevision) {
        WS.verifyResponseStatusCode(accion(dgmm, prep.naveId, 'ADMITIR'), 200)
    }
    return prep
}

/** Estado del tramite y de la nave (HU-09 y HU-03). */
def estadoTramite = { String cookie, def naveId -> json(consultar(cookie, '/api/naves/' + naveId + '/tramite')).estado }
def estadoNave = { String cookie, def naveId -> json(consultar(cookie, '/api/naves/' + naveId)).estado }

List<String> OBLIGATORIOS = ['CERTIFICADO_PROPIEDAD', 'PODER_NOTARIAL', 'ITC', 'SMC']

/**
 * TC-44 — HU-12: la DGMM aprueba un documento vigente y pendiente con el
 * tramite En revision. Responde 200 con el documento revisado (quien y
 * cuando) y el agente lo ve APROBADO en HU-08. Aprobar un documento no mueve
 * el tramite. Una version ya revisada no cambia: revisarla otra vez da 409.
 */
String dgmm = iniciarSesion('revisor@navesitas.pa')
def prep = naveConDocumentos(dgmm, true)
String agente = prep.cookie
def naveId = prep.naveId

def itc = documentosPorTipo(agente, naveId).ITC[0]
assert itc.estado == 'PENDIENTE'

ResponseObject r = revisar(dgmm, naveId, itc.id, 'APROBAR')
WS.verifyResponseStatusCode(r, 200)
def revisado = json(r)
assert revisado.id == itc.id
assert revisado.tipo == 'ITC'
assert revisado.estado == 'APROBADO'
assert revisado.vigente == true
assert revisado.revisadoPor : 'Falta quien reviso'
assert revisado.revisadoEn : 'Falta cuando se reviso'
// Observacion 6 de la revision: se propuso el nombre y no el correo. Se
// deja impreso hasta que el formato este decidido.
println('revisadoPor = ' + revisado.revisadoPor)

// El agente lo ve aprobado; los otros tres siguen pendientes
def porTipo = documentosPorTipo(agente, naveId)
assert porTipo.ITC[0].estado == 'APROBADO'
assert ['CERTIFICADO_PROPIEDAD', 'PODER_NOTARIAL', 'SMC'].every { porTipo[it][0].estado == 'PENDIENTE' }

// Revisar un documento no mueve el tramite
assert estadoTramite(dgmm, naveId) == 'EN_REVISION'

// Una version ya revisada no se vuelve a revisar, ni para aprobar ni para observar
WS.verifyResponseStatusCode(revisar(dgmm, naveId, itc.id, 'APROBAR'), 409)
WS.verifyResponseStatusCode(revisar(dgmm, naveId, itc.id, 'OBSERVAR', 'Cambio de opinion'), 409)
assert documentosPorTipo(agente, naveId).ITC[0].estado == 'APROBADO'
