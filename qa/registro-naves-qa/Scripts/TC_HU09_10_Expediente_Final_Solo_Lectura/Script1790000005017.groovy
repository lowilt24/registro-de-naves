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
    ResponseObject r = Http.enviar(login)
    return Http.sesion(r)
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

/** HU-03: registra una nave con nombre unico y devuelve la respuesta. */
def registrarNave = { String cookie, String prefijo ->
    String nombre = prefijo + ' ' + System.currentTimeMillis()
    return postJson(cookie, '/api/naves', """{
  "nombre": "${nombre}",
  "tipo": "CARGA",
  "servicio": "INTERNACIONAL",
  "tonelajeBruto": 3200.00,
  "tonelajeNeto": 1700.00,
  "eslora": 78.00,
  "manga": 13.00,
  "puntal": 6.50,
  "anioConstruccion": 2017,
  "lugarConstruccion": "Astillero Balboa",
  "materialCasco": "Acero",
  "tipoPropulsion": "Diesel",
  "potenciaKw": 2100.00
}""")
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

/** POST multipart con el tipo y el archivo (igual que TS04). */
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
    cabeceras << new TestObjectProperty('Content-Type', ConditionType.EQUALS, cuerpo.getContentType())
    carga.setHttpHeaderProperties(cabeceras)
    carga.setBodyContent(cuerpo)
    return Http.enviar(carga)
}

/**
 * TC-39 — HU-09, regla agregada por la PO (13-10-2026): en Aprobada,
 * Rechazada y Desistida el expediente queda de solo lectura. Cualquier
 * cambio (documentos, propietarios, agente residente) -> 409. Mientras el
 * tramite esta en curso se edita como siempre. Leer sigue permitido.
 *
 * Implementado el 13-10-2026.
 */
String dgmm = iniciarSesion('revisor@navesitas.pa')
String sufijo = System.currentTimeMillis().toString()

def propietario = { def naveId -> """{
  "nombre": "Naviera Final ${sufijo} S.A.",
  "tipo": "JURIDICA",
  "nacionalidad": "Panamena",
  "domicilio": "Calle 50, Ciudad de Panama",
  "paisConstitucion": "Panama",
  "identificacion": "RUC-F-${sufijo}-${naveId}",
  "naveId": ${naveId}
}""" }
def agenteResidente = { def naveId -> """{
  "nombre": "Bufete Final ${sufijo}",
  "idoneidad": "IDN-F-${sufijo}-${naveId}",
  "telefono": "+507 300-4321",
  "correo": "final${sufijo}@bufete.pa",
  "poderNumero": "PODER-F-${sufijo}-${naveId}",
  "poderFecha": "2026-02-10",
  "poderLugar": "Notaria Quinta del Circuito de Panama"
}""" }

/** Intenta los tres cambios y espera 409 en todos; los documentos no cambian. */
def verificarSoloLectura = { String cookie, def naveId, String estado ->
    int docsAntes = json(consultar(cookie, '/api/naves/' + naveId + '/documentos')).size()
    ResponseObject rDoc = cargarDocumento(cookie, naveId, 'ITC', crearArchivo('itc-final.pdf', pdfValido('final')))
    println(estado + ' / documento: ' + rDoc.getStatusCode() + ' ' + rDoc.getResponseBodyContent())
    String mensaje = 'El trámite ya terminó (' + estado + '); el expediente es de solo lectura.'
    // Se verifica el mensaje: un 409 por otra regla (p. ej. participacion
    // del propietario) no prueba que el expediente este cerrado.
    WS.verifyResponseStatusCode(rDoc, 409)
    WS.verifyEqual(json(rDoc).mensaje, mensaje)
    ResponseObject rProp = postJson(cookie, '/api/propietarios', propietario(naveId))
    WS.verifyResponseStatusCode(rProp, 409)
    WS.verifyEqual(json(rProp).mensaje, mensaje)
    ResponseObject rAgen = postJson(cookie, '/api/naves/' + naveId + '/agente-residente', agenteResidente(naveId))
    WS.verifyResponseStatusCode(rAgen, 409)
    WS.verifyEqual(json(rAgen).mensaje, mensaje)
    // Leer sigue permitido y no cambio nada.
    WS.verifyEqual(json(consultar(cookie, '/api/naves/' + naveId + '/documentos')).size(), docsAntes)
    WS.verifyResponseStatusCode(consultar(cookie, '/api/naves/' + naveId + '/propietarios'), 200)
}

// a) Aprobada.
def aprobada = WebUI.callTestCase(findTestCase('Test Cases/TC_AUX_Nave_Con_Documentos'), [:], FailureHandling.STOP_ON_FAILURE)
// Control: en curso, el expediente se edita (nueva version del ITC).
WS.verifyResponseStatusCode(cargarDocumento(aprobada.cookie, aprobada.naveId, 'ITC',
        crearArchivo('itc-v2.pdf', pdfValido('v2'))), 201)
WS.verifyResponseStatusCode(accion(dgmm, aprobada.naveId, 'ADMITIR'), 200)
WS.verifyResponseStatusCode(accion(dgmm, aprobada.naveId, 'APROBAR'), 200)
verificarSoloLectura(aprobada.cookie, aprobada.naveId, 'Aprobada')

// b) Desistida.
def desistida = WebUI.callTestCase(findTestCase('Test Cases/TC_AUX_Preparar_Nave_Con_Agente'), [:], FailureHandling.STOP_ON_FAILURE)
WS.verifyResponseStatusCode(accion(desistida.cookie, desistida.naveId, 'DESISTIR'), 200)
verificarSoloLectura(desistida.cookie, desistida.naveId, 'Desistida')
