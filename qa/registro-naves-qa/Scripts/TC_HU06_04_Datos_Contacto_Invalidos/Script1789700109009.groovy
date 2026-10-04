import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import groovy.json.JsonSlurper

/**
 * TC-15 — HU-06, negativo: correo y telefono con formato invalido.
 *
 * Criterio de aceptacion: 400 y el error debe nombrar cada campo que
 * fallo, para que la pantalla pueda marcarlos uno por uno.
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
  "nombre": "Nave HU06 Contacto ${sufijo}",
  "tipo": "MIXTA",
  "servicio": "MIXTO",
  "tonelajeBruto": 2200.00,
  "tonelajeNeto": 1100.00,
  "eslora": 88.00,
  "manga": 14.00,
  "puntal": 7.00,
  "anioConstruccion": 2013,
  "lugarConstruccion": "Astillero Balboa",
  "materialCasco": "Acero",
  "tipoPropulsion": "Diesel",
  "potenciaKw": 2600.00
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

String cuerpoPropietario = """{
  "nombre": "Naviera Contacto ${sufijo} S.A.",
  "tipo": "JURIDICA",
  "nacionalidad": "Panamena",
  "domicilio": "Calle 50, Ciudad de Panama",
  "paisConstitucion": "Panama",
  "identificacion": "RUC-CT-${sufijo}",
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

// Telefono con letras y correo sin arroba.
String cuerpoAgente = """{
  "nombre": "Bufete Contacto Malo ${sufijo}",
  "idoneidad": "IDN-CM-${sufijo}",
  "telefono": "llamar al bufete",
  "correo": "esto-no-es-un-correo",
  "poderNumero": "PODER-CM-${sufijo}",
  "poderFecha": "2026-01-05",
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

WS.verifyResponseStatusCode(r, 400)

def error = new JsonSlurper().parseText(r.getResponseBodyContent())
WS.verifyNotEqual(error.campos.telefono, null)
WS.verifyNotEqual(error.campos.correo, null)

// La nave sigue sin agente designado.
RequestObject consultar = new RequestObject('consultarAgente')
consultar.setRestUrl(base + '/api/naves/' + naveId + '/agente-residente')
consultar.setRestRequestMethod('GET')
consultar.setHttpHeaderProperties([
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])
WS.verifyResponseStatusCode(WS.sendRequest(consultar), 404)
