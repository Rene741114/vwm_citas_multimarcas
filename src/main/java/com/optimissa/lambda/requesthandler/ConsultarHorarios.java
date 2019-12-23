package com.optimissa.lambda.requesthandler;

import java.util.List;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.optimissa.lambda.dto.request.ReqConsultaHorarios;
import com.optimissa.lambda.dto.response.RespuestaHorariosAtencionDMS;
import com.optimissa.lambda.util.CodigosRespuesta;

public class ConsultarHorarios extends ManagerAmazonSQSQueues implements RequestHandler<ReqConsultaHorarios, RespuestaHorariosAtencionDMS> {

	private static String URL_COLA_ENTRADA = "https://sqs.us-east-2.amazonaws.com/811219751427/vwmx_resp_consultar_horario_to_lambda";
	private static String URL_COLA_SALIDA = "https://sqs.us-east-2.amazonaws.com/811219751427/vwmx_req_consultar_horario_to_dms";

	@Override
	public RespuestaHorariosAtencionDMS handleRequest(ReqConsultaHorarios params, Context context) {
		context.getLogger().log("ConsultarHorarios.handleRequest()");
		context.getLogger().log("\n" + params);

		// Envia el mensaje a la Queue del DMS
		sendMessageRequest(params, URL_COLA_SALIDA);

		RespuestaHorariosAtencionDMS respuestaHorariosAtencionDMS = searchRespuestaHorariosAtencionDMS(
				params.getIdConversacion(), URL_COLA_ENTRADA);

		return respuestaHorariosAtencionDMS;
	}

	public RespuestaHorariosAtencionDMS searchRespuestaHorariosAtencionDMS(Long idConversacion, String urlCola) {
		AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
		ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(urlCola)
				.withWaitTimeSeconds(WITH_WAIT_TIME_SECONDS);
		List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();

		System.out.println("\n" + messages.size() + " mensajes de respuesta encontrados.");
		System.out.print("Buscando: [idConversacion = " + idConversacion + "]");

		RespuestaHorariosAtencionDMS respuesta = new RespuestaHorariosAtencionDMS();
		for (Message message : messages) {
			// Busca el idConversacion en el mensaje
			String msgBody = message.getBody();
			if (msgBody.contains("idConversacion") && msgBody.contains(String.valueOf(idConversacion))) {
				try {
					System.out.println(", mensaje encontrado... ");
					respuesta = mapperJson.readValue(msgBody, RespuestaHorariosAtencionDMS.class);
					deleteMessageRequest(message, urlCola);
					if (respuesta.isExito()) {
						configureRespuestaDMS(respuesta, true, CodigosRespuesta.SOLICITUD_ATENDIDA_CON_EXITO);
					}
					// TODO Enviar este mensaje en una cola de INSERTS para Postgress
				} catch (Exception e) {
					System.out.println("\tOcurrio un error inesperado...");
					e.printStackTrace();
				}
			}
		}

		return respuesta;
	}
	
}
