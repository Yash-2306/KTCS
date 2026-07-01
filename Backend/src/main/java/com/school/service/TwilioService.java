package com.school.service;

import org.springframework.stereotype.Service;

@Service
public class TwilioService {

    // Twilio credentials placeholders - to be configured in application.properties later
    private static final String ACCOUNT_SID = "ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
    private static final String AUTH_TOKEN = "your_auth_token_here";
    private static final String FROM_NUMBER = "+12345678901"; // Twilio phone number

    public boolean sendMessage(String toPhone, String messageBody) {
        System.out.println("====== TWILIO SMS SERVICE PLACEHOLDER ======");
        System.out.println("Attempting to send message via Twilio:");
        System.out.println("From: " + FROM_NUMBER);
        System.out.println("To: " + toPhone);
        System.out.println("Message: " + messageBody);
        
        /* 
        // Actual Twilio implementation to be enabled later:
        try {
            // Initialize Twilio
            com.twilio.Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
            
            // Send Message
            com.twilio.rest.api.v2010.account.Message message = com.twilio.rest.api.v2010.account.Message.creator(
                new com.twilio.type.PhoneNumber(toPhone), // To
                new com.twilio.type.PhoneNumber(FROM_NUMBER), // From
                messageBody // Message body
            ).create();
            
            System.out.println("Message sent successfully! SID: " + message.getSid());
            return true;
        } catch (Exception e) {
            System.err.println("Error sending message via Twilio: " + e.getMessage());
            return false;
        }
        */
        
        System.out.println("Status: SIMULATED SUCCESS (Software integration pending)");
        System.out.println("=============================================");
        return true;
    }
}
