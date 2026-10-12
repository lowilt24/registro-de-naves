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
    HttpFormDataBodyContent cuerpo = new HttpFormDataBodyContent([
        new FormDataBodyParameter('tipo', tipo, FormDataBodyParameter.PARAM_TYPE_TEXT),
        new FormDataBodyParameter('archivo', archivo.absolutePath, FormDataBodyParameter.PARAM_TYPE_FILE)
    ])
    // Katalon no pone solo el Content-Type cuando la peticion se arma por
    // codigo. Sin esta cabecera el servidor recibe la carga sin tipo y
    // responde 415 antes de mirar el archivo. getContentType() incluye el
    // boundary, que es lo que separa las partes del multipart.
    cabeceras << new TestObjectProperty('Content-Type', ConditionType.EQUALS, cuerpo.getContentType())
    carga.setHttpHeaderProperties(cabeceras)
    carga.setBodyContent(cuerpo)
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
 * TC-17 — HU-07, positivo: cargar un PDF valido.
 *
 * Criterios 1, 2 y 7: el documento queda con tipo, nombre, tamano, hash
 * SHA-256, version y estado PENDIENTE; recargar el mismo tipo crea la
 * version 2 y la 1 sigue en el historial.
 */
def prep = WebUI.callTestCase(findTestCase('Test Cases/TC_AUX_Preparar_Nave_Con_Agente'), [:], FailureHandling.STOP_ON_FAILURE)
String cookie = prep.cookie
def naveId = prep.naveId

byte[] contenido = pdfValido('ITC version 1')
File archivo = crearArchivo('itc-prueba.pdf', contenido)

ResponseObject r = cargarDocumento(cookie, naveId, 'ITC', archivo)
println('Carga: ' + r.getStatusCode() + ' ' + r.getResponseBodyContent())
WS.verifyResponseStatusCode(r, 201)

def doc = json(r)
WS.verifyEqual(doc.tipo, 'ITC')
WS.verifyEqual(doc.naveId, naveId)
WS.verifyEqual(doc.version, 1)
WS.verifyEqual(doc.estado, 'PENDIENTE')
WS.verifyEqual(doc.tamanoBytes as Long, contenido.length as Long)
// El hash lo calcula el servidor; tiene que coincidir con el del archivo enviado.
WS.verifyEqual(doc.hashSha256, sha256(contenido))
WS.verifyNotEqual(doc.nombreArchivo, null)

// --- Criterio 7: recargar el mismo tipo genera version nueva ---
byte[] contenido2 = pdfValido('ITC version 2 corregida')
ResponseObject r2 = cargarDocumento(cookie, naveId, 'ITC', crearArchivo('itc-corregido.pdf', contenido2))
WS.verifyResponseStatusCode(r2, 201)
WS.verifyEqual(json(r2).version, 2)

// Sin historial: solo la vigente.
def vigentes = json(consultar(cookie, '/api/naves/' + naveId + '/documentos'))
WS.verifyEqual(vigentes.size(), 1)
WS.verifyEqual(vigentes[0].version, 2)

// Con historial: las dos, y la 1 ya no es vigente.
def todas = json(consultar(cookie, '/api/naves/' + naveId + '/documentos?historial=true'))
WS.verifyEqual(todas.size(), 2)
WS.verifyEqual(todas.find { it.version == 1 }.vigente, false)
WS.verifyEqual(todas.find { it.version == 2 }.vigente, true)
