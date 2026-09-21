import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import groovy.json.JsonSlurper

/**
 * TC-13 — HU-06, negativo: no se puede designar agente residente a una
 * nave que todavia no tiene propietarios.
 *
 * Esta es la precondicion central de HU-06 y la razon por la que el
 * Sprint 3 saco el propietario del registro de la nave: con el modelo
 * anterior esta verificacion nunca podia fallar.
 *
 * Criterio de aceptacion: 409 con un mensaje que explique la causa.
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

// Nave recien registrada, SIN propietarios vinculados.
String cuerpoNave = """{
  "nombre": "Nave HU06 Huerfana ${sufijo}",
  "tipo": "REMOLCADOR",
  "servicio": "CABOTAJE",
  "tonelajeBruto": 500.00,
  "tonelajeNeto": 260.00,
  "eslora": 32.00,
  "manga": 10.00,
  "puntal": 4.50,
  "anioConstruccion": 2015,
  "lugarConstruccion": "Astillero Balboa",
  "materialCasco": "Acero",
  "tipoPropulsion": "Diesel",
  "potenciaKw": 2200.00
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

String cuerpoAgente = """{
  "nombre": "Bufete Prematuro ${sufijo}",
  "idoneidad": "IDN-P-${sufijo}",
  "telefono": "+507 300-9999",
  "correo": "prematuro${sufijo}@bufete.pa",
  "poderNumero": "PODER-P-${sufijo}",
  "poderFecha": "2026-02-01",
  "poderLugar": "Ciudad de Panama"
}"""

RequestObject designar = new RequestObject('designarAgente')
designar.setRestUrl(base + '/api/naves/' + naveId + '/agente-residente')
designar.setRestRequestMethod('POST')
designar.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json'),
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])
designar.setBodyContent(new HttpTextBodyContent(cuerpoAgente))

ResponseObject r = WS.sendRequest(designar)
println('Designacion status: ' + r.getStatusCode())
println('Respuesta: ' + r.getResponseBodyContent())

WS.verifyResponseStatusCode(r, 409)
WS.verifyMatch(r.getResponseBodyContent(), '.*propietarios.*', true)

// No debe haber quedado ninguna designacion.
RequestObject consultar = new RequestObject('consultarAgente')
consultar.setRestUrl(base + '/api/naves/' + naveId + '/agente-residente')
consultar.setRestRequestMethod('GET')
consultar.setHttpHeaderProperties([
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])
WS.verifyResponseStatusCode(WS.sendRequest(consultar), 404)
