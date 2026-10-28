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


/**
 * TC-32 — HU-09 (D2, P5): En revision --rechazar--> Rechazada, con
 * observacion obligatoria. Rechazada es final.
 */
def prep = WebUI.callTestCase(findTestCase('Test Cases/TC_AUX_Nave_Con_Documentos'), [:], FailureHandling.STOP_ON_FAILURE)
String agente = prep.cookie
def naveId = prep.naveId
String dgmm = iniciarSesion('revisor@navesitas.pa')

WS.verifyResponseStatusCode(accion(dgmm, naveId, 'ADMITIR'), 200)
WS.verifyEqual(json(consultar(dgmm, '/api/naves/' + naveId + '/tramite')).acciones, ['OBSERVAR', 'APROBAR', 'RECHAZAR'])

WS.verifyResponseStatusCode(accion(dgmm, naveId, 'RECHAZAR'), 400)
WS.verifyResponseStatusCode(accion(dgmm, naveId, 'RECHAZAR', ''), 400)
WS.verifyResponseStatusCode(accion(dgmm, naveId, 'RECHAZAR', 'x' * 1001), 400)
WS.verifyEqual(json(consultar(dgmm, '/api/naves/' + naveId + '/tramite')).estado, 'EN_REVISION')

ResponseObject rRechazar = accion(dgmm, naveId, 'RECHAZAR', 'La nave no cumple el requisito de arqueo.')
WS.verifyResponseStatusCode(rRechazar, 200)
WS.verifyEqual(json(rRechazar).estado, 'RECHAZADA')
WS.verifyEqual(json(rRechazar).esFinal, true)

WS.verifyResponseStatusCode(accion(dgmm, naveId, 'OBSERVAR', 'Tarde'), 409)
WS.verifyResponseStatusCode(accion(agente, naveId, 'DESISTIR'), 409)

def h = json(consultar(agente, '/api/naves/' + naveId + '/tramite/historial'))
WS.verifyEqual(h.last().accion, 'RECHAZAR')
WS.verifyEqual(h.last().observacion, 'La nave no cumple el requisito de arqueo.')
