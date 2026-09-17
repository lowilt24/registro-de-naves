import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS

String base = 'http://localhost:8080'
String nombre = 'Nave Inexistente ' + System.currentTimeMillis()

RequestObject consulta = new RequestObject('disponibilidad')
consulta.setRestUrl(base + '/api/naves/disponibilidad?nombre=' +
    java.net.URLEncoder.encode(nombre, 'UTF-8'))
consulta.setRestRequestMethod('GET')

ResponseObject r = WS.sendRequest(consulta)
println('Respuesta: ' + r.getResponseBodyContent())

WS.verifyResponseStatusCode(r, 200)
WS.verifyMatch(r.getResponseBodyContent(), '.*"disponible":true.*', true)
WS.verifyMatch(r.getResponseBodyContent(), '.*Nombre disponible.*', true)