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
    ResponseObject r = WS.sendRequest(login)
    return r.getHeaderFields()['Set-Cookie']?.get(0)?.split(';')?.getAt(0)
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
    return WS.sendRequest(p)
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
 * TC-28 — HU-09 (D1, P2): 403 cuando el rol o la relacion con la nave no
 * dan la accion. Se verifica antes que la transicion: el agente recibe
 * 403 al aprobar aunque el tramite este en "Solicitud presentada".
 */
String agente = iniciarSesion('agente@navesitas.pa')
String dgmm = iniciarSesion('revisor@navesitas.pa')
def otro = WebUI.callTestCase(findTestCase('Test Cases/TC_AUX_Crear_Segundo_Agente'), [:], FailureHandling.STOP_ON_FAILURE)

def naveId = json(registrarNave(agente, 'Permisos')).id

ResponseObject rAprobar = accion(agente, naveId, 'APROBAR')
WS.verifyResponseStatusCode(rAprobar, 403)
WS.verifyEqual(json(rAprobar).mensaje, 'Solo un funcionario de la DGMM puede aprobar el trámite.')
WS.verifyResponseStatusCode(accion(agente, naveId, 'RECHAZAR', 'Intento del agente'), 403)
WS.verifyResponseStatusCode(accion(agente, naveId, 'ADMITIR'), 403)

WS.verifyResponseStatusCode(accion(dgmm, naveId, 'DESISTIR'), 403)

// Agente ajeno: ni lee ni actua.
WS.verifyResponseStatusCode(consultar(otro.cookie, '/api/naves/' + naveId + '/tramite'), 403)
WS.verifyResponseStatusCode(consultar(otro.cookie, '/api/naves/' + naveId + '/tramite/historial'), 403)
ResponseObject rAjeno = accion(otro.cookie, naveId, 'DESISTIR')
WS.verifyResponseStatusCode(rAjeno, 403)
WS.verifyEqual(json(rAjeno).mensaje, 'La nave indicada no le pertenece.')

WS.verifyEqual(json(consultar(agente, '/api/naves/' + naveId + '/tramite')).estado, 'SOLICITUD_PRESENTADA')
WS.verifyEqual(json(consultar(agente, '/api/naves/' + naveId + '/tramite/historial')).size(), 1)
