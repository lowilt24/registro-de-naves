import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import groovy.json.JsonSlurper

/**
 * TC-12 — HU-06, positivo: designar el agente residente de una nave que
 * ya tiene propietario.
 *
 * Criterio de aceptacion: 201, la designacion queda vigente y la
 * consulta del agente vigente devuelve los mismos datos.
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

// --- 1. Nave propia ---
String cuerpoNave = """{
  "nombre": "Nave HU06 ${sufijo}",
  "tipo": "CARGA",
  "servicio": "INTERNACIONAL",
  "tonelajeBruto": 3000.00,
  "tonelajeNeto": 1600.00,
  "eslora": 100.00,
  "manga": 16.00,
  "puntal": 8.50,
  "anioConstruccion": 2020,
  "lugarConstruccion": "Astillero Balboa",
  "materialCasco": "Acero",
  "tipoPropulsion": "Diesel",
  "potenciaKw": 3800.00
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

// --- 2. Precondicion de HU-06: la nave necesita propietario ---
String cuerpoPropietario = """{
  "nombre": "Naviera HU06 ${sufijo} S.A.",
  "tipo": "JURIDICA",
  "nacionalidad": "Panamena",
  "domicilio": "Calle 50, Ciudad de Panama",
  "paisConstitucion": "Panama",
  "identificacion": "RUC-A6-${sufijo}",
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

// --- 3. Designar el agente residente ---
String cuerpoAgente = """{
  "nombre": "Bufete Maritimo ${sufijo}",
  "idoneidad": "IDN-${sufijo}",
  "telefono": "+507 300-1234",
  "correo": "contacto${sufijo}@bufete.pa",
  "poderNumero": "PODER-${sufijo}",
  "poderFecha": "2026-01-15",
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

WS.verifyResponseStatusCode(r, 201)

def creado = new JsonSlurper().parseText(r.getResponseBodyContent())
WS.verifyEqual(creado.vigente, true)
WS.verifyEqual(creado.naveId, naveId)
WS.verifyEqual(creado.nombre, 'Bufete Maritimo ' + sufijo)
// El backend normaliza el correo a minusculas.
WS.verifyEqual(creado.correo, 'contacto' + sufijo + '@bufete.pa')

// --- 4. La consulta del agente vigente devuelve lo mismo ---
RequestObject consultar = new RequestObject('consultarAgente')
consultar.setRestUrl(base + '/api/naves/' + naveId + '/agente-residente')
consultar.setRestRequestMethod('GET')
consultar.setHttpHeaderProperties([
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])

ResponseObject rConsulta = WS.sendRequest(consultar)
println('Consulta: ' + rConsulta.getResponseBodyContent())

WS.verifyResponseStatusCode(rConsulta, 200)
def vigente = new JsonSlurper().parseText(rConsulta.getResponseBodyContent())
WS.verifyEqual(vigente.id, creado.id)
WS.verifyEqual(vigente.vigente, true)
