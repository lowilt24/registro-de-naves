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
 * TC-41 — HU-11: /api/tramites/conteo-por-estado devuelve los seis estados
 * de todas las naves, incluso en cero; el total es la suma; cada numero
 * coincide con la lista filtrada y se mueve cuando cambia un tramite.
 */
String agente = iniciarSesion('agente@navesitas.pa')
String dgmm = iniciarSesion('revisor@navesitas.pa')

def conteo = { ->
    ResponseObject r = consultar(dgmm, '/api/tramites/conteo-por-estado')
    WS.verifyResponseStatusCode(r, 200)
    def c = json(r)
    println('Conteo: ' + c)
    assert c.estados.keySet() == ESTADOS as Set : 'Deben venir los seis estados, tambien los que estan en cero'
    assert c.total == c.estados.values().sum() : 'El total no es la suma de los estados'
    return c
}

def antes = conteo()

// Cada numero es el largo de la lista filtrada
for (String estado : ESTADOS) {
    int filas = json(consultar(dgmm, '/api/tramites?estado=' + estado)).size()
    assert filas == antes.estados[estado] : estado + ': conteo ' + antes.estados[estado] + ' y lista ' + filas
}

// Registrar suma uno en Solicitud presentada; desistir lo pasa a Desistida
def nave = json(registrarNave(agente, 'Bandeja Conteo')).id
def despuesDeRegistrar = conteo()
assert despuesDeRegistrar.estados.SOLICITUD_PRESENTADA == antes.estados.SOLICITUD_PRESENTADA + 1
assert despuesDeRegistrar.total == antes.total + 1

WS.verifyResponseStatusCode(accion(agente, nave, 'DESISTIR'), 200)
def despuesDeDesistir = conteo()
assert despuesDeDesistir.estados.SOLICITUD_PRESENTADA == antes.estados.SOLICITUD_PRESENTADA
assert despuesDeDesistir.estados.DESISTIDA == antes.estados.DESISTIDA + 1
assert despuesDeDesistir.total == antes.total + 1
