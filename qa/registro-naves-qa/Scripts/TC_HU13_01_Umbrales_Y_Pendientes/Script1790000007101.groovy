import navesitas.Hu13 as H
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
String agente = H.login('agente@navesitas.pa')
def casos = [
    [[eslora:23.99], 'NO_APLICA','NO_APLICA'],
    [[eslora:24], 'OBLIGATORIO','NO_APLICA'],
    [[eslora:24.01], 'OBLIGATORIO','NO_APLICA'],
    [[tonelajeBruto:499], 'NO_APLICA','NO_APLICA'],
    [[tonelajeBruto:500], 'NO_APLICA','OBLIGATORIO'],
    [[tonelajeBruto:501], 'NO_APLICA','OBLIGATORIO'],
    [[tipo:'PASAJEROS',tonelajeBruto:100], 'NO_APLICA','OBLIGATORIO'],
    [[servicio:'CABOTAJE'], 'REQUIERE_REVISION','REQUIERE_REVISION'],
    [[servicio:'MIXTO'], 'REQUIERE_REVISION','REQUIERE_REVISION'],
    [[tipo:'MIXTA'], 'REQUIERE_REVISION','REQUIERE_REVISION'],
    [[tipo:'TANQUERO'], 'REQUIERE_REVISION','REQUIERE_REVISION']
]
casos.each { caso ->
    def id = H.nave(agente, caso[0])
    def respuesta = H.pedir(agente, '/api/naves/'+id+'/documentos/requisitos')
    WS.verifyResponseStatusCode(respuesta, 200)
    def requisitos = H.json(respuesta).collectEntries { [(it.tipo):it] }
    assert requisitos.ITC.estado == caso[1]
    assert requisitos.SMC.estado == caso[2]
    assert requisitos.CERTIFICADO_PROPIEDAD.estado == 'OBLIGATORIO'
    assert requisitos.PODER_NOTARIAL.estado == 'OBLIGATORIO'
    assert requisitos.values().every { it.explicacion }
}
