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
 * TC-21 — HU-08, positivo: consultar una nave con documentos.
 *
 * Criterio 1: cada documento trae tipo, fecha de carga, version y estado.
 */
def prep = WebUI.callTestCase(findTestCase('Test Cases/TC_AUX_Preparar_Nave_Con_Agente'), [:], FailureHandling.STOP_ON_FAILURE)
String cookie = prep.cookie
def naveId = prep.naveId

WS.verifyResponseStatusCode(cargarDocumento(cookie, naveId, 'ITC', crearArchivo('itc.pdf', pdfValido('itc'))), 201)
WS.verifyResponseStatusCode(cargarDocumento(cookie, naveId, 'SMC', crearArchivo('smc.pdf', pdfValido('smc'))), 201)

ResponseObject r = consultar(cookie, '/api/naves/' + naveId + '/documentos')
println('Consulta: ' + r.getResponseBodyContent())
WS.verifyResponseStatusCode(r, 200)

def lista = json(r)
WS.verifyEqual(lista.size(), 2)
WS.verifyEqual(lista*.tipo.toSet(), ['ITC', 'SMC'].toSet())
lista.each { d ->
    WS.verifyNotEqual(d.creadoEn, null)
    WS.verifyEqual(d.version, 1)
    WS.verifyEqual(d.estado, 'PENDIENTE')
    WS.verifyEqual(d.vigente, true)
}
