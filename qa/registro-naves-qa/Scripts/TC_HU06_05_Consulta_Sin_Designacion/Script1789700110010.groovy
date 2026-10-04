import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import groovy.json.JsonSlurper

/**
 * TC-16 — HU-06, negativo: consultar el agente de una nave que aun no
 * lo tiene designado.
 *
 * Criterio de aceptacion: 404 con un mensaje claro, y el historial
 * responde una lista vacia en vez de fallar. La pantalla de expediente
 * usa las dos respuestas para decidir que mostrar.
 */

String base = 'http://localhost:8080'

RequestObject login = new RequestObject('login')
login.setRestUrl(base + '/api/auth/login')
login.setRestRequestMethod('POST')
login.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS,
        'application/x-www-form-urlencoded')
])
login.setBodyContent(new HttpTextBodyContent(
    'correo=agente@navesitas.pa&password=Navesitas2026*'))

ResponseObject rLogin = WS.sendRequest(login)
String cookie = rLogin.getHeaderFields()['Set-Cookie']?.get(0)?.split(';')?.getAt(0)

String sufijo = System.currentTimeMillis()

String cuerpoNave = """{
  "nombre": "Nave HU06 Sin Agente ${sufijo}",
  "tipo": "PASAJEROS",
  "servicio": "CABOTAJE",
  "tonelajeBruto": 900.00,
  "tonelajeNeto": 480.00,
  "eslora": 45.00,
  "manga": 11.00,
  "puntal": 5.00,
  "anioConstruccion": 2022,
  "lugarConstruccion": "Astillero Balboa",
  "materialCasco": "Aluminio",
  "tipoPropulsion": "Diesel",
  "potenciaKw": 1400.00
}"""

RequestObject crearNave = new RequestObject('crearNave')
crearNave.setRestUrl(base + '/api/naves')
crearNave.setRestRequestMethod('POST')
crearNave.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json'),
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])
crearNave.setBodyContent(new HttpTextBodyContent(cuerpoNave))

ResponseObject rNave = WS.sendRequest(crearNave)
WS.verifyResponseStatusCode(rNave, 201)
def naveId = new JsonSlurper().parseText(rNave.getResponseBodyContent()).id

// La nave tiene propietario, pero todavia no se le designo agente.
String cuerpoPropietario = """{
  "nombre": "Naviera Sin Agente ${sufijo} S.A.",
  "tipo": "JURIDICA",
  "nacionalidad": "Panamena",
  "domicilio": "Calle 50, Ciudad de Panama",
  "paisConstitucion": "Panama",
  "identificacion": "RUC-SA-${sufijo}",
  "naveId": ${naveId}
}"""

RequestObject crearProp = new RequestObject('registrarPropietario')
crearProp.setRestUrl(base + '/api/propietarios')
crearProp.setRestRequestMethod('POST')
crearProp.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json'),
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])
crearProp.setBodyContent(new HttpTextBodyContent(cuerpoPropietario))
WS.verifyResponseStatusCode(WS.sendRequest(crearProp), 201)

// --- Consulta del vigente: 404 ---
RequestObject consultar = new RequestObject('consultarAgente')
consultar.setRestUrl(base + '/api/naves/' + naveId + '/agente-residente')
consultar.setRestRequestMethod('GET')
consultar.setHttpHeaderProperties([
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])

ResponseObject r = WS.sendRequest(consultar)
println('Consulta status: ' + r.getStatusCode())
println('Respuesta: ' + r.getResponseBodyContent())

WS.verifyResponseStatusCode(r, 404)
WS.verifyMatch(r.getResponseBodyContent(), '.*"mensaje".*', true)

// --- El historial responde vacio, no error ---
RequestObject historial = new RequestObject('historialAgentes')
historial.setRestUrl(base + '/api/naves/' + naveId + '/agente-residente/historial')
historial.setRestRequestMethod('GET')
historial.setHttpHeaderProperties([
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])

ResponseObject rHistorial = WS.sendRequest(historial)
println('Historial: ' + rHistorial.getResponseBodyContent())

WS.verifyResponseStatusCode(rHistorial, 200)
WS.verifyEqual(new JsonSlurper().parseText(rHistorial.getResponseBodyContent()).size(), 0)
