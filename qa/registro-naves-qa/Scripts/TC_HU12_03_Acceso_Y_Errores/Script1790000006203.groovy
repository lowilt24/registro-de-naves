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
 * TC-46 — HU-12, seguridad: solo la DGMM revisa. Sin sesion 401; el agente
 * dueno y el auditor 403; nave inexistente, documento inexistente o de otra
 * nave 404 (IDOR, igual que la descarga de HU-08); decision vacia o
 * inventada y caracter nulo (SEC-08) 400. Ninguno de esos intentos cambia el
 * documento.
 *
 * El bloque "Orden" sigue el criterio del plan (401 -> 404 -> 403 -> 409 ->
 * 400). DISENO-V7.md propone 401 -> 400 -> 404 -> 403 -> 409; si el equipo
 * adopta ese, los dos codigos de ese bloque pasan a 400.
 */
String dgmm = iniciarSesion('revisor@navesitas.pa')
String auditor = iniciarSesion('auditor@navesitas.pa')
def prepA = naveConDocumentos(dgmm, true)
def prepB = naveConDocumentos(dgmm, true)
String agente = prepA.cookie
def naveA = prepA.naveId
def itcA = documentosPorTipo(agente, naveA).ITC[0]
def itcB = documentosPorTipo(prepB.cookie, prepB.naveId).ITC[0]

// 401 sin sesion
WS.verifyResponseStatusCode(revisar(null, naveA, itcA.id, 'APROBAR'), 401)

// 403: el agente dueno de la nave y el auditor no revisan
WS.verifyResponseStatusCode(revisar(agente, naveA, itcA.id, 'APROBAR'), 403)
WS.verifyResponseStatusCode(revisar(auditor, naveA, itcA.id, 'APROBAR'), 403)

// 404: nave inexistente, documento inexistente, documento de otra nave
WS.verifyResponseStatusCode(revisar(dgmm, 999999999, itcA.id, 'APROBAR'), 404)
WS.verifyResponseStatusCode(revisar(dgmm, naveA, 999999999, 'APROBAR'), 404)
WS.verifyResponseStatusCode(revisar(dgmm, naveA, itcB.id, 'APROBAR'), 404)

// 400: cuerpo invalido
String ruta = '/api/naves/' + naveA + '/documentos/' + itcA.id + '/revision'
WS.verifyResponseStatusCode(postJson(dgmm, ruta, '{}'), 400)
WS.verifyResponseStatusCode(postJson(dgmm, ruta, '{"decision": ""}'), 400)
WS.verifyResponseStatusCode(revisar(dgmm, naveA, itcA.id, 'BORRAR'), 400)
WS.verifyResponseStatusCode(postJson(dgmm, ruta, '{"decision": "APROBAR"'), 400)

// 400: caracter nulo en la observacion (SEC-08), nunca 500
ResponseObject nulo = revisar(dgmm, naveA, itcA.id, 'OBSERVAR', 'Firma\u0000ilegible')
WS.verifyResponseStatusCode(nulo, 400)

// Nada de lo anterior cambio los documentos
assert documentosPorTipo(agente, naveA).ITC[0].estado == 'PENDIENTE'
assert documentosPorTipo(prepB.cookie, prepB.naveId).ITC[0].estado == 'PENDIENTE'

// Orden (criterio del plan): con dos errores a la vez gana el primero
// - agente con decision inventada: 403 antes que 400
WS.verifyResponseStatusCode(revisar(agente, naveA, itcA.id, 'BORRAR'), 403)
// - DGMM, tramite en Solicitud presentada y observar sin observacion: 409 antes que 400
def prepC = naveConDocumentos(dgmm, false)
def itcC = documentosPorTipo(prepC.cookie, prepC.naveId).ITC[0]
WS.verifyResponseStatusCode(revisar(dgmm, prepC.naveId, itcC.id, 'OBSERVAR'), 409)
