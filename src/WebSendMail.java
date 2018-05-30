import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.StringTokenizer;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/WebSendMail")
public class WebSendMail extends HttpServlet {

	String to = "받을 메일주소";

	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		if (request.getContentType().startsWith("multipart/form-data")) {
			try {
				HashMap data = getMailData(request, response);
				sendMail(data);

				ServletContext sc = getServletContext();
				RequestDispatcher rd = sc.getRequestDispatcher("/thankyou.html");
				rd.forward(request, response);
			} catch (MessagingException ex) {
				throw new ServletException(ex);
			}
		} else {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	private HashMap getMailData(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException, MessagingException {
		String boundary = request.getHeader("Content-Type");
		int pos = boundary.indexOf('=');
		boundary = boundary.substring(pos + 1);
		boundary = "--" + boundary;
		ServletInputStream in = request.getInputStream();
		byte[] bytes = new byte[512];
		int state = 0;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		String name = null, value = null, filename = null, contentType = null;
		HashMap mailData = new HashMap();

		int i = in.readLine(bytes, 0, 512);
		while (-1 != i) {
			String st = new String(bytes, 0, i);
			if (st.startsWith(boundary)) {
				state = 0;
				if (null != name) {
					if (value != null)
						// -2 to remove CR/LF
						mailData.put(name, value.substring(0, value.length() - 2));
					else if (buffer.size() > 2) {
						MimeBodyPart bodyPart = new MimeBodyPart();
						DataSource ds = new ByteArrayDataSource(buffer.toByteArray(), contentType, filename);
						bodyPart.setDataHandler(new DataHandler(ds));
						bodyPart.setDisposition("attachment; filename=\"" + filename + "\"");
						bodyPart.setFileName(filename);
						mailData.put(name, bodyPart);
					}
					name = null;
					value = null;
					filename = null;
					contentType = null;
					buffer = new ByteArrayOutputStream();
				}
			} else if (st.startsWith("Content-Disposition: form-data") && state == 0) {
				StringTokenizer tokenizer = new StringTokenizer(st, ";=\"");
				while (tokenizer.hasMoreTokens()) {
					String token = tokenizer.nextToken();
					if (token.startsWith(" name")) {
						name = tokenizer.nextToken();
						state = 2;
					} else if (token.startsWith(" filename")) {
						filename = tokenizer.nextToken();
						StringTokenizer ftokenizer = new StringTokenizer(filename, "\\/:");
						filename = ftokenizer.nextToken();
						while (ftokenizer.hasMoreTokens())
							filename = ftokenizer.nextToken();
						state = 1;
						break;
					}
				}
			} else if (st.startsWith("Content-Type") && state == 1) {
				pos = st.indexOf(":");
				// + 2 to remove the space
				// - 2 to remove CR/LF
				contentType = st.substring(pos + 2, st.length() - 2);
			} else if (st.equals("\r\n") && state == 1)
				state = 3;
			else if (st.equals("\r\n") && state == 2)
				state = 4;
			else if (state == 4)
				value = value == null ? st : value + st;
			else if (state == 3)
				buffer.write(bytes, 0, i);
			i = in.readLine(bytes, 0, 512);
		}
		return mailData;
	}

	private void sendMail(HashMap mailData) throws MessagingException {
		System.setProperty("mail.smtp.starttls.enable", "true"); // gmail�� 臾댁“嫄� true 怨좎젙
		System.setProperty("mail.smtp.auth", "true"); // gmail�� 臾댁“嫄� true 怨좎젙
		System.setProperty("mail.smtp.host", "smtp.gmail.com"); // smtp �꽌踰� 二쇱냼
		System.setProperty("mail.smtp.port", "587"); // gmail �룷�듃

		Authenticator auth = new MyAuthentication();
		Message msg = new MimeMessage(Session.getDefaultInstance(System.getProperties(), auth));
		
		//諛쏅뒗�궗�엺
		InternetAddress[] tos = InternetAddress.parse(to);
		msg.setRecipients(Message.RecipientType.TO, tos);
		//�젣紐�
		msg.setSubject("GoldFishEdu::Teacher form submitted");
		msg.setSentDate(new Date());
		//蹂몃Ц
		BodyPart body = new MimeBodyPart();
		String firstName = (String) mailData.get("firstName");
		String lastName = (String) mailData.get("lastName");
		String email = (String) mailData.get("email");
		String birth = (String) mailData.get("birth");
		String citizenship = (String) mailData.get("citizenship");
		String gender = (String) mailData.get("gender");
		String startDate = (String) mailData.get("startDate");
		String locationPref = (String) mailData.get("locationPref");
		String studentPref = (String) mailData.get("studentPref");
		
		body.setText("First Name: " + firstName + "\r\nLast Name: " +lastName + "\r\nEmail: " +email + "\r\nBirth: " +birth + "\r\nCitizenship: " +citizenship + "\r\nGender: " +gender + "\r\nStart Date: " +startDate + "\r\nLocation Preference: " +locationPref + "\r\nStudent Preference: " +studentPref);
		MimeMultipart multipart = new MimeMultipart();
		multipart.addBodyPart(body);
		//泥⑤��뙆�씪
		BodyPart resume = (BodyPart) mailData.get("resume");
		BodyPart picture = (BodyPart) mailData.get("picture");
		multipart.addBodyPart(resume);
		multipart.addBodyPart(picture);
		msg.setContent(multipart);

		Transport.send(msg);
	}

	class MyAuthentication extends Authenticator {

		private PasswordAuthentication pa;
		private String id;
		private String pw;

		private MyAuthentication() {

			id = "보내는 내 gmail 계정"; 
			pw = "비밀번호"; 
			pa = new PasswordAuthentication(id, pw);
		}

		// �떆�뒪�뀥�뿉�꽌 �궗�슜�븯�뒗 �씤利앹젙蹂�
		public PasswordAuthentication getPasswordAuthentication() {
			return pa;
		}
	}

}
