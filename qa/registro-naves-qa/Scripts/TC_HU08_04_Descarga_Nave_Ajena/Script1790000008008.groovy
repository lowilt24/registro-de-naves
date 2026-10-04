import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.FormDataBodyParameter
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import com.kms.katalon.core.testobject.impl.HttpFormDataBodyContent
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import com.kms.katalon.core.model.FailureHandling
import static com.kms.katalon.core.testcase.TestCaseFactory.findTestCase
import groovy.json.JsonSlurper
import java.security.MessageDigest

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
    ResponseObject r = WS.sendRequest(login)
    return r.getHeaderFields()['Set-Cookie']?.get(0)?.split(';')?.getAt(0)
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

def sha256 = { byte[] datos ->
    MessageDigest.getInstance('SHA-256').digest(datos).encodeHex().toString()
}

/** POST multipart con el tipo y el archivo. No se fija Content-Type: Katalon pone el boundary. */
def cargarDocumento = { String cookie, def naveId, String tipo, File archivo ->
    RequestObject carga = new RequestObject('cargarDocumento')
    carga.setRestUrl(base + '/api/naves/' + naveId + '/documentos')
    carga.setRestRequestMethod('POST')
    List<TestObjectProperty> cabeceras = []
    if (cookie) cabeceras << new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
    carga.setHttpHeaderProperties(cabeceras)
    carga.setBodyContent(new HttpFormDataBodyContent([
        new FormDataBodyParameter('tipo', tipo, FormDataBodyParameter.PARAM_TYPE_TEXT),
        new FormDataBodyParameter('archivo', archivo.absolutePath, FormDataBodyParameter.PARAM_TYPE_FILE)
    ]))
    return WS.sendRequest(carga)
}

/** GET con la cookie de sesion (o sin ella si es null). */
def consultar = { String cookie, String ruta ->
    RequestObject get = new RequestObject('consultar')
    get.setRestUrl(base + ruta)
    get.setRestRequestMethod('GET')
    List<TestObjectProperty> cabeceras = []
    if (cookie) cabeceras << new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
    get.setHttpHeaderProperties(cabeceras)
    return WS.sendRequest(get)
}

def json = { ResponseObject r -> new JsonSlurper().parseText(r.getResponseBodyContent()) }

/**
 * TC-24 — HU-08: la descarga verifica de nuevo la propiedad (criterio 5)
 * y la consulta exige sesion (criterio 6).
 *
 * Tres intentos de otro usuario:
 *  a) descargar por la ruta de la nave duena         -> 403
 *  b) IDOR: pedir el documento ajeno colgado de una
 *     nave propia, para pasar el control de la nave   -> 404
 *  c) listar los documentos de la nave ajena          -> 403
 * Y sin sesion -> 401.
 */
def prep = WebUI.callTestCase(findTestCase('Test Cases/TC_AUX_Preparar_Nave_Con_Agente'), [:], FailureHandling.STOP_ON_FAILURE)
String cookie = prep.cookie
def naveId = prep.naveId

ResponseObject rCarga = cargarDocumento(cookie, naveId, 'ITC', crearArchivo('itc.pdf', pdfValido('itc')))
WS.verifyResponseStatusCode(rCarga, 201)
def documentoId = json(rCarga).id
String rutaArchivo = '/api/naves/' + naveId + '/documentos/' + documentoId + '/archivo'

// El dueno si puede descargar, y recibe el PDF.
ResponseObject propia = consultar(cookie, rutaArchivo)
WS.verifyResponseStatusCode(propia, 200)
assert propia.getResponseBodyContent().startsWith('%PDF-') : 'La descarga no devolvio un PDF'

// --- Otro usuario ---
String cookieAjeno = iniciarSesion('revisor@navesitas.pa')
assert cookieAjeno : 'No se pudo iniciar sesion con revisor@navesitas.pa'

// a) por la ruta de la nave duena
WS.verifyResponseStatusCode(consultar(cookieAjeno, rutaArchivo), 403)

// b) IDOR: el revisor registra una nave suya y cuelga de ella el id ajeno
RequestObject naveRevisor = new RequestObject('naveRevisor')
naveRevisor.setRestUrl(base + '/api/naves')
naveRevisor.setRestRequestMethod('POST')
naveRevisor.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json'),
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookieAjeno)
])
naveRevisor.setBodyContent(new HttpTextBodyContent("""{
  "nombre": "Nave Revisor ${System.currentTimeMillis()}",
  "tipo": "REMOLCADOR", "servicio": "CABOTAJE",
  "tonelajeBruto": 300.00, "tonelajeNeto": 150.00,
  "eslora": 25.00, "manga": 8.00, "puntal": 3.50,
  "anioConstruccion": 2012, "lugarConstruccion": "Astillero Balboa",
  "materialCasco": "Acero", "tipoPropulsion": "Diesel", "potenciaKw": 900.00
}"""))
ResponseObject rNaveRevisor = WS.sendRequest(naveRevisor)
WS.verifyResponseStatusCode(rNaveRevisor, 201)
def naveDelRevisor = json(rNaveRevisor).id

ResponseObject idor = consultar(cookieAjeno,
    '/api/naves/' + naveDelRevisor + '/documentos/' + documentoId + '/archivo')
println('IDOR: ' + idor.getStatusCode())
WS.verifyResponseStatusCode(idor, 404)

// c) listar la nave ajena
WS.verifyResponseStatusCode(consultar(cookieAjeno, '/api/naves/' + naveId + '/documentos'), 403)

// --- Sin sesion ---
WS.verifyResponseStatusCode(consultar(null, '/api/naves/' + naveId + '/documentos'), 401)
WS.verifyResponseStatusCode(consultar(null, rutaArchivo), 401)
