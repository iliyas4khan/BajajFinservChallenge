package com.example.webhookapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@EnableRetry
public class WebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);
    private static final String WEBHOOK_GENERATE_URL = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook";
    
    @Autowired
    private RestTemplate restTemplate;

    public void processWebhook() {
        // Step 1: Make the initial POST request
        logger.info("Initiating webhook generation request...");
        
        // Create request body
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("name", "John Doe");
        requestBody.put("regNo", "REG12347");
        requestBody.put("email", "john@example.com");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<WebhookResponse> response = restTemplate.postForEntity(
                    WEBHOOK_GENERATE_URL, request, WebhookResponse.class);
            
            WebhookResponse webhookResponse = response.getBody();
            logger.info("Webhook generated successfully: {}", webhookResponse.getWebhook());
            
            // Step 2: Process the problem and get the solution
            Map<String, Object> result = processResult(webhookResponse);
            
            // Step 3: Send the result to the webhook
            sendResultToWebhook(webhookResponse.getWebhook(), webhookResponse.getAccessToken(), result);
            
        } catch (Exception e) {
            logger.error("Error during webhook processing", e);
        }
    }
    
    private Map<String, Object> processResult(WebhookResponse response) {
        Map<String, Object> result = new HashMap<>();
        result.put("regNo", "REG12347");
        
        // Check if we need to solve Question 1 or Question 2
        // For REG12347, the last digit is 7 (odd), so we solve Question 1
        
        if (response.getData().containsKey("users")) {
            Object usersObj = response.getData().get("users");
            
            // Question 1: Find mutual followers
            List<List<Integer>> mutualFollowers = findMutualFollowers(usersObj);
            result.put("outcome", mutualFollowers);
            logger.info("Solved Question 1: Mutual Followers");
        } else if (response.getData().containsKey("n") && response.getData().containsKey("findId")) {
            // Question 2: Find nth level followers
            int n = Integer.parseInt(response.getData().get("n").toString());
            int findId = Integer.parseInt(response.getData().get("findId").toString());
            List<Integer> nthFollowers = findNthLevelFollowers(n, findId, response.getData());
            result.put("outcome", nthFollowers);
            logger.info("Solved Question 2: Nth Level Followers");
        }
        
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private List<List<Integer>> findMutualFollowers(Object usersObj) {
        List<Map<String, Object>> users;
        
        if (usersObj instanceof List) {
            users = (List<Map<String, Object>>) usersObj;
        } else {
            logger.error("Users data format is not as expected");
            return new ArrayList<>();
        }
        
        List<List<Integer>> result = new ArrayList<>();
        Map<Integer, Set<Integer>> followMap = new HashMap<>();
        
        // Build the follow map
        for (Map<String, Object> user : users) {
            int userId = ((Number) user.get("id")).intValue();
            List<Integer> follows = (List<Integer>) user.get("follows");
            followMap.put(userId, new HashSet<>(follows));
        }
        
        // Find mutual follows
        for (Map<String, Object> user : users) {
            int userId = ((Number) user.get("id")).intValue();
            Set<Integer> userFollows = followMap.get(userId);
            
            for (Integer followedId : userFollows) {
                Set<Integer> followedUserFollows = followMap.get(followedId);
                
                if (followedUserFollows != null && followedUserFollows.contains(userId)) {
                    // Mutual follow found, add [min, max] once
                    if (userId < followedId) {
                        result.add(Arrays.asList(userId, followedId));
                    }
                }
            }
        }
        
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private List<Integer> findNthLevelFollowers(int n, int findId, Map<String, Object> data) {
        List<Map<String, Object>> users = (List<Map<String, Object>>) data.get("users");
        Map<Integer, List<Integer>> followsMap = new HashMap<>();
        
        // Build the follows map
        for (Map<String, Object> user : users) {
            int userId = ((Number) user.get("id")).intValue();
            List<Integer> follows = (List<Integer>) user.get("follows");
            followsMap.put(userId, follows);
        }
        
        // BFS to find nth level followers
        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        queue.add(findId);
        visited.add(findId);
        
        int currentLevel = 0;
        
        // Process level by level
        while (!queue.isEmpty() && currentLevel < n) {
            int levelSize = queue.size();
            currentLevel++;
            
            for (int i = 0; i < levelSize; i++) {
                int currentId = queue.poll();
                List<Integer> follows = followsMap.get(currentId);
                
                if (follows != null) {
                    for (Integer followId : follows) {
                        if (!visited.contains(followId)) {
                            visited.add(followId);
                            queue.add(followId);
                        }
                    }
                }
            }
        }
        
        // After BFS, queue contains all nodes at exactly level n
        List<Integer> result = new ArrayList<>(queue);
        Collections.sort(result);
        return result;
    }
    
    @Retryable(maxAttempts = 4, backoff = @Backoff(delay = 1000))
    private void sendResultToWebhook(String webhookUrl, String accessToken, Map<String, Object> result) {
        logger.info("Sending result to webhook: {}", webhookUrl);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", accessToken);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(result, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    webhookUrl, HttpMethod.POST, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Result successfully sent to webhook");
            } else {
                logger.error("Error sending result to webhook: {}", response.getStatusCode());
                throw new RuntimeException("Error sending result to webhook");
            }
        } catch (Exception e) {
            logger.error("Exception while sending result to webhook", e);
            throw e; // Retry will be triggered
        }
    }
}