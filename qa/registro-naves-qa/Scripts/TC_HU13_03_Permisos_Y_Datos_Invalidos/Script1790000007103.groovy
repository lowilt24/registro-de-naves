import navesitas.Hu13 as H
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
String agente = H.login('agente@navesitas.pa')
def id = H.nave(agente)
String ruta = '/api/naves/'+id+'/documentos/requisitos'
WS.verifyResponseStatusCode(H.pedir(null,ruta),401)
['agente@navesitas.pa','revisor@navesitas.pa','auditor@navesitas.pa'].each { WS.verifyResponseStatusCode(H.pedir(H.login(it),ruta),200) }
WS.verifyResponseStatusCode(H.pedir(H.login('admin@navesitas.pa'),ruta),403)
WS.verifyResponseStatusCode(H.pedir(agente,'/api/naves/999999999/documentos/requisitos'),404)
// JSON bien formado con datos incompletos o enum ajeno al modelo: 400, nunca 500.
[[servicio:'NACIONAL'],[servicio:null],[tipo:'NO_CLASIFICADO'],[eslora:-1],[tonelajeBruto:0]].each { invalido ->
    Map datos = [nombre:'Invalida HU Trece '+System.nanoTime(),tipo:'CARGA',servicio:'INTERNACIONAL',tonelajeBruto:499,
        tonelajeNeto:100,eslora:24,manga:10,puntal:5,anioConstruccion:2022,lugarConstruccion:'Panama',
        materialCasco:'Acero',tipoPropulsion:'Diesel',potenciaKw:1000] + invalido
    WS.verifyResponseStatusCode(H.pedir(agente,'/api/naves',datos),400)
}
