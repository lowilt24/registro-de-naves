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
 * Caso auxiliar, NO va en ninguna suite.
 *
 * Deja una nave lista para HU-07: registrada por agente@navesitas.pa, con
 * un propietario y un agente residente vigente (la precondicion del
 * criterio 8). Los casos TC-17 a TC-24 lo llaman con callTestCase y
 * reciben [cookie, naveId].
 */
String sufijo = System.currentTimeMillis().toString()
String cookie = iniciarSesion('agente@navesitas.pa')
assert cookie : 'No se pudo iniciar sesion con agente@navesitas.pa'

def postJson = { String ruta, String cuerpo ->
    RequestObject p = new RequestObject('post')
    p.setRestUrl(base + ruta)
    p.setRestRequestMethod('POST')
    p.setHttpHeaderProperties([
        new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json'),
        new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
    ])
    p.setBodyContent(new HttpTextBodyContent(cuerpo))
    return WS.sendRequest(p)
}

ResponseObject rNave = postJson('/api/naves', """{
  "nombre": "Nave Documentos ${sufijo}",
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
WS.verifyResponseStatusCode(rNave, 201)
def naveId = json(rNave).id

WS.verifyResponseStatusCode(postJson('/api/propietarios', """{
  "nombre": "Naviera Documentos ${sufijo} S.A.",
  "tipo": "JURIDICA",
  "nacionalidad": "Panamena",
  "domicilio": "Calle 50, Ciudad de Panama",
  "paisConstitucion": "Panama",
  "identificacion": "RUC-D-${sufijo}",
  "naveId": ${naveId}
}"""), 201)

WS.verifyResponseStatusCode(postJson('/api/naves/' + naveId + '/agente-residente', """{
  "nombre": "Bufete Documentos ${sufijo}",
  "idoneidad": "IDN-D-${sufijo}",
  "telefono": "+507 300-4321",
  "correo": "docs${sufijo}@bufete.pa",
  "poderNumero": "PODER-D-${sufijo}",
  "poderFecha": "2026-02-10",
  "poderLugar": "Notaria Quinta del Circuito de Panama"
}"""), 201)

println('Nave preparada: ' + naveId)
return [cookie: cookie, naveId: naveId]
