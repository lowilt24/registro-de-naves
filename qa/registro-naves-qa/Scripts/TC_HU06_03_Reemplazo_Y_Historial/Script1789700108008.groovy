import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import groovy.json.JsonSlurper

/**
 * TC-14 — HU-06, positivo: reemplazar el agente residente conserva al
 * anterior como historial.
 *
 * Criterio de aceptacion: al designar un segundo agente, el primero
 * deja de estar vigente pero NO se borra. El expediente de
 * abanderamiento necesita saber quien gestionaba el tramite en cada
 * momento. Solo puede haber un agente vigente por nave.
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
  "nombre": "Nave HU06 Relevo ${sufijo}",
  "tipo": "TANQUERO",
  "servicio": "INTERNACIONAL",
  "tonelajeBruto": 8000.00,
  "tonelajeNeto": 4200.00,
  "eslora": 140.00,
  "manga": 21.00,
  "puntal": 11.00,
  "anioConstruccion": 2014,
  "lugarConstruccion": "Astillero Balboa",
  "materialCasco": "Acero",
  "tipoPropulsion": "Diesel",
  "potenciaKw": 7200.00
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
  "nombre": "Naviera Relevo ${sufijo} S.A.",
  "tipo": "JURIDICA",
  "nacionalidad": "Panamena",
  "domicilio": "Calle 50, Ciudad de Panama",
  "paisConstitucion": "Panama",
  "identificacion": "RUC-RL-${sufijo}",
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

// --- Primer agente ---
String primerAgente = """{
  "nombre": "Bufete Saliente ${sufijo}",
  "idoneidad": "IDN-S-${sufijo}",
  "telefono": "+507 300-1111",
  "correo": "saliente${sufijo}@bufete.pa",
  "poderNumero": "PODER-S-${sufijo}",
  "poderFecha": "2026-01-10",
  "poderLugar": "Ciudad de Panama"
}"""

RequestObject designar1 = new RequestObject('designarPrimero')
designar1.setRestUrl(base + '/api/naves/' + naveId + '/agente-residente')
designar1.setRestRequestMethod('POST')
designar1.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json'),
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])
designar1.setBodyContent(new HttpTextBodyContent(primerAgente))

ResponseObject r1 = WS.sendRequest(designar1)
println('Primer agente status: ' + r1.getStatusCode())
WS.verifyResponseStatusCode(r1, 201)
def agenteAnterior = new JsonSlurper().parseText(r1.getResponseBodyContent())

// --- Segundo agente: reemplaza al primero ---
String segundoAgente = """{
  "nombre": "Bufete Entrante ${sufijo}",
  "idoneidad": "IDN-E-${sufijo}",
  "telefono": "+507 300-2222",
  "correo": "entrante${sufijo}@bufete.pa",
  "poderNumero": "PODER-E-${sufijo}",
  "poderFecha": "2026-03-20",
  "poderLugar": "Ciudad de Panama"
}"""

RequestObject designar2 = new RequestObject('designarSegundo')
designar2.setRestUrl(base + '/api/naves/' + naveId + '/agente-residente')
designar2.setRestRequestMethod('POST')
designar2.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json'),
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])
designar2.setBodyContent(new HttpTextBodyContent(segundoAgente))

ResponseObject r2 = WS.sendRequest(designar2)
println('Segundo agente status: ' + r2.getStatusCode())
println('Respuesta: ' + r2.getResponseBodyContent())
WS.verifyResponseStatusCode(r2, 201)
def agenteNuevo = new JsonSlurper().parseText(r2.getResponseBodyContent())
WS.verifyEqual(agenteNuevo.vigente, true)

// --- El vigente ahora es el entrante ---
RequestObject consultar = new RequestObject('consultarAgente')
consultar.setRestUrl(base + '/api/naves/' + naveId + '/agente-residente')
consultar.setRestRequestMethod('GET')
consultar.setHttpHeaderProperties([
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])

ResponseObject rConsulta = WS.sendRequest(consultar)
WS.verifyResponseStatusCode(rConsulta, 200)
def vigente = new JsonSlurper().parseText(rConsulta.getResponseBodyContent())
WS.verifyEqual(vigente.id, agenteNuevo.id)
WS.verifyEqual(vigente.nombre, 'Bufete Entrante ' + sufijo)

// --- El historial conserva los dos, con un solo vigente ---
RequestObject historial = new RequestObject('historialAgentes')
historial.setRestUrl(base + '/api/naves/' + naveId + '/agente-residente/historial')
historial.setRestRequestMethod('GET')
historial.setHttpHeaderProperties([
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])

ResponseObject rHistorial = WS.sendRequest(historial)
println('Historial: ' + rHistorial.getResponseBodyContent())

WS.verifyResponseStatusCode(rHistorial, 200)
def lista = new JsonSlurper().parseText(rHistorial.getResponseBodyContent())

// El anterior no se borro.
WS.verifyEqual(lista.size(), 2)
WS.verifyEqual(lista.count { it.vigente == true }, 1)
WS.verifyEqual(lista.find { it.id == agenteNuevo.id }.vigente, true)
WS.verifyEqual(lista.find { it.id == agenteAnterior.id }.vigente, false)
