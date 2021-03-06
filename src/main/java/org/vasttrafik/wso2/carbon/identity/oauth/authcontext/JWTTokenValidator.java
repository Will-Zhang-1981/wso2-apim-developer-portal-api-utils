package org.vasttrafik.wso2.carbon.identity.oauth.authcontext;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
//import org.apache.openjpa.lib.util.ParseException;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.Certificate;
import java.text.ParseException;
import java.util.Enumeration;

//import org.json.simple.JSONObject;
//import org.json.simple.parser.JSONParser;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;

import org.wso2.carbon.base.ServerConfiguration;

import com.nimbusds.jwt.JWTClaimsSet;
 
/**
 * Simple JWT processor for validation of JWT generated by WSO2 IS.
 */
public final class JWTTokenValidator {
	
	private static final Log log = LogFactory.getLog(JWTTokenValidator.class);
 
	private static final Base64 base64Url = new Base64(true);
 
	private static String trustStore = "" ;
	private static String trustStorePassword = "";
 
	private JSONObject jsonHeaderObject;
	private JSONObject jsonClaimObject;
 
	private String jwtToken;
 
	public JWTTokenValidator(String jwtToken) {
		this.jwtToken = jwtToken;
	}
	
	public boolean isValid() {
		@SuppressWarnings("deprecation")
		JSONParser parser = new JSONParser();
		
		String[] jwtTokenValues = jwtToken.split("\\.");
		
		if(jwtTokenValues.length > 0) {
			String value = new String(base64Url.decode(jwtTokenValues[0].getBytes()));
 
			try {
				jsonHeaderObject = (JSONObject)parser.parse(value);
				
				if (log.isDebugEnabled())
					log.debug(jsonHeaderObject.toJSONString());
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
		}
 
		String jwtAssertion = null;
		
		if(jwtTokenValues.length > 1) {
			String value = new String(base64Url.decode(jwtTokenValues[1].getBytes()));
			jwtAssertion = jwtTokenValues[0] + "." + jwtTokenValues[1];
 
			try {
				jsonClaimObject = (JSONObject) parser.parse(value);
				
				if (log.isDebugEnabled())
					log.debug(jsonClaimObject.toJSONString());
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
		}
 
		byte[] jwtSignature = null;
		
		if(jwtTokenValues.length > 2){
			jwtSignature = base64Url.decode(jwtTokenValues[2].getBytes());
		}
 
		KeyStore keyStore = null;
		
		String thumbPrint = new String(base64Url.decode(((String) jsonHeaderObject.get("x5t")).getBytes()));
		String signatureAlgo = (String) jsonHeaderObject.get("alg");
 
		if("RS256".equals(signatureAlgo)){
			signatureAlgo = "SHA256withRSA";
		} 
		else if("RS515".equals(signatureAlgo)){
			signatureAlgo = "SHA512withRSA";
		} 
		else if("RS384".equals(signatureAlgo)){
			signatureAlgo = "SHA384withRSA";
		} 
		else {
			signatureAlgo = "SHA256withRSA";
		}
 
		if(jwtAssertion != null && jwtSignature != null) {
			try {
				keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
				keyStore.load(new FileInputStream(trustStore), trustStorePassword.toCharArray());
				
				String alias = getAliasForX509CertThumb(thumbPrint.getBytes(), keyStore);
				
				if (log.isDebugEnabled()) 
					log.debug("Retrieving certificate for alias:" + alias);
				
				Certificate certificate = keyStore.getCertificate(alias);
				
				if (log.isDebugEnabled()) 
					log.debug("Retrieved ertificate:" + certificate.toString());
				
				Signature signature = Signature.getInstance(signatureAlgo);
				signature.initVerify(certificate);
				signature.update(jwtAssertion.getBytes());
				
				return signature.verify(jwtSignature);
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
		} 
		else {
			log.error("Signature is null");
		}
		return false;
	}
	
	public JSONObject getJsonHeaderObject() {
		return jsonHeaderObject;
	}
 
	public JSONObject getJsonClaimObject() {
		return jsonClaimObject;
	}
	
	public JWTClaims getJWTClaims() {
		return new JWTClaims(jsonClaimObject);
	}
	
	public JWTToken getJWTToken() {
		try {
			JWTClaimsSet claimsSet = JWTClaimsSet.parse(jsonClaimObject);
			return new JWTToken(claimsSet, jwtToken);
		}
		catch (ParseException e) {
			e.printStackTrace();
		}
		return null;
	}
 
	private String getAliasForX509CertThumb(byte[] thumb, KeyStore keyStore) {
		Certificate cert = null;
		MessageDigest sha = null;
 
		try {
			sha = MessageDigest.getInstance("SHA-1");
 
			for (Enumeration<String> e = keyStore.aliases(); e.hasMoreElements();) {
				String alias = e.nextElement();
				Certificate[] certs = keyStore.getCertificateChain(alias);
 
				if (certs == null || certs.length == 0) {
					cert = keyStore.getCertificate(alias);
 
					if (cert == null) {
						return null;
					}
				} 
				else {
					cert = certs[0];
				}
 
				sha.update(cert.getEncoded());
				byte[] data = sha.digest();
 
				if (new String(thumb).equals(hexify(data))) {
					return alias;
				}
			}
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
 
	private String hexify(byte bytes[]) {
		char[] hexDigits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
 
		StringBuilder buf = new StringBuilder(bytes.length * 2);
 
		for (byte aByte : bytes) {
			buf.append(hexDigits[(aByte & 0xf0) >> 4]);
			buf.append(hexDigits[aByte & 0x0f]);
		}
 
		return buf.toString();
	}
 
	public static void main(String[] args){
		// sample JWT
		String jwtToken = "eyJhbGciOiJSUzI1NiIsIng1dCI6Ik5tSm1PR1V4TXpabFlqTTJaRFJoTlRabFlUQTFZemRoWlRSaU9XRTBOV0kyTTJKbU9UYzFaQSJ9.eyJodHRwOlwvXC93c28yLm9yZ1wvZ2F0ZXdheVwvYXBwbGljYXRpb25uYW1lIjoicG9ydGFsLWFwaSxjb21tdW5pdHktYXBpLGlkZW50aXR5LW1nbXQtYXBpIiwiZXhwIjoxNDU4MzA3OTA2LCJodHRwOlwvXC93c28yLm9yZ1wvZ2F0ZXdheVwvZXhwIjoxNDU4MzA3OTA2NDI2LCJzdWIiOiJsYXNzZSIsImlzcyI6Imh0dHA6XC9cL3dzbzIub3JnXC9nYXRld2F5IiwiaHR0cDpcL1wvd3NvMi5vcmdcL2dhdGV3YXlcL2VuZHVzZXIiOiJsYXNzZSIsImh0dHA6XC9cL3dzbzIub3JnXC9jbGFpbXNcL3JvbGUiOiJJbnRlcm5hbFwvc3Vic2NyaWJlcixJbnRlcm5hbFwvZXZlcnlvbmUiLCJodHRwOlwvXC93c28yLm9yZ1wvY2xhaW1zXC9pZGVudGl0eVwvaWQiOiIyIiwiaWF0IjoxNDU4MzA3MDA2fQ.CxkR21Ad9F2LENpwT4o_ZeqSViZM4M2W_N8pQu5xXmxilhjYAJcO4cvPnRCcO7I_5hrTzHgWKW0LDS4cbHscLxI4O-Rfr2aKT2ivICoUl8ybgkWrsr8uUrHDV_1Iaj2joCke0nSy09liBKcH1UpTiT5aXy5aXMhlFag6Qx2ZFnw";
		
		JWTTokenValidator processor = new JWTTokenValidator(jwtToken);
 
		if(processor.isValid()){
			JSONObject body = processor.getJsonClaimObject();
			System.out.println(body.toJSONString());
		} 
		else {
			System.err.println("Signature verification failed.");
		}
	}
	
	static {
		try {
			/**
			 * Get the server configuration (carbon.xml) and the trust store password "VTcert2017intern"
			 */
			trustStorePassword = ServerConfiguration.getInstance()
					.getFirstProperty("Security.TrustStore.Password");
			
			/**
			 * Get the truststore location
			 */
			trustStore = System.getProperty("carbon.home") + 
					"/repository/resources/security/client-truststore.jks";
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
