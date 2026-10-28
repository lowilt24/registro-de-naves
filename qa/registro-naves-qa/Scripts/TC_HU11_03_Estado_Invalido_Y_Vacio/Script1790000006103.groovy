import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import navesitas.Http
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import com.kms.katalon.core.model.FailureHandling
import static com.kms.katalon.core.testcase.TestCaseFactory.findTestCase
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.time.OffsetDateTime

String base = 'http://localhost:8080'

/** POST /api/auth/login; devuelve la respuesta (302 con Location y Set-Cookie). */
def entrar = { String correo ->
    RequestObject login = new RequestObject('login')
    login.setRestUrl(base + '/api/auth/login')
    login.setRestRequestMethod('POST')
    login.setHttpHeaderProperties([
        new TestObjectProperty('Content-Type', ConditionType.EQUALS,
            'application/x-www-form-urlencoded')
    ])
    login.setBodyContent(new HttpTextBodyContent(
        'correo=' + correo + '&password=Navesitas2026*'))
    return Http.enviar(login)
}

/** Inicia sesion y devuelve la cookie JSESSIONID. */
def iniciarSesion = { String correo -> Http.sesion(entrar(correo)) }

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

/** HU-09: POST /api/naves/{id}/tramite/estado con la accion. */
def accion = { String cookie, def naveId, String nombre ->
    ResponseObject r = postJson(cookie, '/api/naves/' + naveId + '/tramite/estado', JsonOutput.toJson([accion: nombre]))
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

List<String> ESTADOS = ['SOLICITUD_PRESENTADA', 'EN_REVISION', 'OBSERVADA', 'APROBADA', 'RECHAZADA', 'DESISTIDA']


/**
 * TC-42 — HU-11: un estado que no existe da 400 con mensaje, nunca 500
 * (tambien en minusculas, un estado de la nave y una inyeccion). Un estado
 * sin tramites devuelve [] con 200, que la pantalla convierte en mensaje.
 */
String dgmm = iniciarSesion('revisor@navesitas.pa')

for (String malo : ['VOLANDO', 'en_revision', 'REGISTRADA', '1', "'%20OR%20'1'='1"]) {
    ResponseObject r = consultar(dgmm, '/api/tramites?estado=' + malo)
    println(malo + ' -> ' + r.getStatusCode() + ' ' + r.getResponseBodyContent())
    WS.verifyResponseStatusCode(r, 400)
    assert json(r).mensaje : 'El 400 debe traer mensaje'
}

// Estado sin tramites: lista vacia, no error. Depende de los datos de la
// base; si ningun estado esta en cero, lo cubre BandejaIntegrationTest.bandejaVacia.
def conteo = json(consultar(dgmm, '/api/tramites/conteo-por-estado'))
def vacios = ESTADOS.findAll { conteo.estados[it] == 0 }
println('Estados sin tramites en esta base: ' + vacios)
for (String estado : vacios) {
    ResponseObject r = consultar(dgmm, '/api/tramites?estado=' + estado)
    WS.verifyResponseStatusCode(r, 200)
    assert json(r) == [] : estado + ' esta en cero y la lista no viene vacia'
}
if (vacios.isEmpty()) {
    println('Ningun estado en cero: la lista vacia la cubre BandejaIntegrationTest.bandejaVacia')
}
