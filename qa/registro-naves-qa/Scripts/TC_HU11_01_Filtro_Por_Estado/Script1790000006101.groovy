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
 * TC-40 — HU-11 (D1): la bandeja de la DGMM lista los tramites de todas las
 * naves. Con ?estado=X trae solo ese estado; sin filtro, Solicitud
 * presentada y En revision, la ultima accion mas antigua primero. Cada fila
 * trae nave, agente, estado, ultima accion y dias en espera.
 */
String agente = iniciarSesion('agente@navesitas.pa')
String dgmm = iniciarSesion('revisor@navesitas.pa')
String auditor = iniciarSesion('auditor@navesitas.pa')

def presentada = json(registrarNave(agente, 'Bandeja Presentada')).id
def desistida = json(registrarNave(agente, 'Bandeja Desistida')).id
WS.verifyResponseStatusCode(accion(agente, desistida, 'DESISTIR'), 200)
def prep = WebUI.callTestCase(findTestCase('Test Cases/TC_AUX_Nave_Con_Documentos'), [:], FailureHandling.STOP_ON_FAILURE)
def enRevision = prep.naveId
WS.verifyResponseStatusCode(accion(dgmm, enRevision, 'ADMITIR'), 200)

// Filtro por un estado
ResponseObject rDesistidas = consultar(dgmm, '/api/tramites?estado=DESISTIDA')
WS.verifyResponseStatusCode(rDesistidas, 200)
def desistidas = json(rDesistidas)
assert desistidas*.naveId.contains(desistida) : 'Falta la nave desistida en el filtro'
assert !desistidas*.naveId.contains(presentada) : 'El filtro trajo una nave de otro estado'
assert desistidas.every { it.estado == 'DESISTIDA' } : 'Hay tramites de otro estado en el filtro'

def fila = desistidas.find { it.naveId == desistida }
println('Fila: ' + fila)
assert fila.nave.startsWith('Bandeja Desistida')
assert fila.agente == 'Agente Naviero de Prueba'
assert fila.ultimaAccion != null
assert fila.diasEnEspera == 0 : 'La nave se movio hoy: 0 dias en espera'

// Sin filtro: lo que espera a la DGMM, el mas antiguo primero
def porAtender = json(consultar(dgmm, '/api/tramites'))
println('Sin filtro: ' + porAtender.size() + ' tramites')
assert porAtender.every { it.estado in ['SOLICITUD_PRESENTADA', 'EN_REVISION'] }
assert porAtender*.naveId.contains(presentada)
assert porAtender*.naveId.contains(enRevision)
assert !porAtender*.naveId.contains(desistida)
List<OffsetDateTime> horas = porAtender.collect { OffsetDateTime.parse(it.ultimaAccion) }
for (int i = 1; i < horas.size(); i++) {
    assert !horas[i - 1].isAfter(horas[i]) : 'La bandeja no va de la mas antigua a la mas nueva (fila ' + i + ')'
}
assert porAtender*.naveId.indexOf(presentada) < porAtender*.naveId.indexOf(enRevision) :
    'La presentada (mas antigua) debe ir antes que la recien admitida'

// El auditor ve la misma bandeja (solo lectura)
ResponseObject rAuditor = consultar(auditor, '/api/tramites')
WS.verifyResponseStatusCode(rAuditor, 200)
assert json(rAuditor)*.naveId.contains(presentada)
