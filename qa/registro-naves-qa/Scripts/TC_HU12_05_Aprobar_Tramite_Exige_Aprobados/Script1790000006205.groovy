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
 * TC-48 — HU-12 (D4, D5): aprobar el tramite exige los cuatro documentos
 * obligatorios vigentes en APROBADO. Si no, 409 con "faltantes" en el orden
 * del enum (como el 409 de Admitir), y ni el tramite ni la nave cambian. Un
 * documento OBSERVADO, o su version nueva PENDIENTE, sigue faltando. Con los
 * cuatro aprobados: 200, tramite Aprobada y nave ABANDERADA.
 */
String dgmm = iniciarSesion('revisor@navesitas.pa')
def prep = naveConDocumentos(dgmm, true)
String agente = prep.cookie
def naveId = prep.naveId
def porTipo = documentosPorTipo(agente, naveId)

def aprobarTramiteFalla = { List<String> faltantes ->
    ResponseObject r = accion(dgmm, naveId, 'APROBAR')
    WS.verifyResponseStatusCode(r, 409)
    assert json(r).faltantes == faltantes
    assert estadoTramite(dgmm, naveId) == 'EN_REVISION'
    assert estadoNave(agente, naveId) == 'REGISTRADA'
}

// Ninguno aprobado: faltan los cuatro, en el orden del enum
aprobarTramiteFalla(OBLIGATORIOS)

// Tres aprobados: falta el SMC
for (String tipo : ['CERTIFICADO_PROPIEDAD', 'PODER_NOTARIAL', 'ITC']) {
    WS.verifyResponseStatusCode(revisar(dgmm, naveId, porTipo[tipo][0].id, 'APROBAR'), 200)
}
aprobarTramiteFalla(['SMC'])

// SMC observado: sigue faltando
WS.verifyResponseStatusCode(revisar(dgmm, naveId, porTipo.SMC[0].id, 'OBSERVAR', 'El SMC no corresponde a la nave.'), 200)
aprobarTramiteFalla(['SMC'])

// Version nueva del SMC (PENDIENTE): sigue faltando hasta que se apruebe
WS.verifyResponseStatusCode(cargarDocumento(agente, naveId, 'SMC'), 201)
def smcNuevo = documentosPorTipo(agente, naveId).SMC[0]
aprobarTramiteFalla(['SMC'])

// Los cuatro aprobados: el tramite se aprueba y la nave queda abanderada (D5)
WS.verifyResponseStatusCode(revisar(dgmm, naveId, smcNuevo.id, 'APROBAR'), 200)
WS.verifyResponseStatusCode(accion(dgmm, naveId, 'APROBAR'), 200)
assert estadoTramite(dgmm, naveId) == 'APROBADA'
assert estadoNave(agente, naveId) == 'ABANDERADA'
