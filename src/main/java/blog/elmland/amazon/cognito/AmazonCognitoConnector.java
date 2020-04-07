/*
 * Copyright 2018 Florian Schmidt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package blog.elmland.amazon.cognito;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.ListUsersRequest;
import com.amazonaws.services.cognitoidp.model.ListUsersResult;
import com.amazonaws.services.cognitoidp.model.TooManyRequestsException;
import com.amazonaws.services.cognitoidp.model.UserType;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Florian Schmidt https://elmland.blog
 */
@Slf4j
public class AmazonCognitoConnector {

	private final static String AWS_ACCESS_KEY = "<YOUR AWS ACCESS KEY>";
	private final static String AWS_SECRET_KEY = "<YOUR AWS SECRET KEY>";
	private final static String AWS_USER_POOL_ID = "<YOUR USER POOL ID>";
	private final static String AWS_REGION = "<YOUR AWS REGION>";

	private AWSCognitoIdentityProvider identityProvider;

	public AmazonCognitoConnector() {
		BasicAWSCredentials creds = new BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY);

		AWSCognitoIdentityProviderClientBuilder builder = AWSCognitoIdentityProviderClientBuilder.standard()
				.withCredentials(new AWSStaticCredentialsProvider(creds));
		builder.setRegion(AWS_REGION);

		this.identityProvider = builder.build();
	}

	public List<MyUserModel> listAllUsers(int limit) {
		/** check limit 0<limit<=60 "Maximum number of users to be returned" */
		if (limit <= 0 || limit > 60) {
			throw new IllegalArgumentException("limit must have a value less than or equal to 60");
		}

		List<MyUserModel> users = new ArrayList<>();

		/** prepare cognito list users request */
		ListUsersRequest listUsersRequest = new ListUsersRequest();
		listUsersRequest.withUserPoolId(AWS_USER_POOL_ID);
		listUsersRequest.setLimit(limit);

		/** send list users request */
		ListUsersResult result = identityProvider.listUsers(listUsersRequest);

		List<UserType> userTypeList = result.getUsers();
		users.addAll(userTypeList.stream().map(u -> convertCognitoUser(u)).collect(Collectors.toList()));

		/**
		 * as long as there is a pagination token in the list users result => resend
		 * list users request with pagination token.
		 */
		while (result.getPaginationToken() != null) {
			try {
				listUsersRequest.setPaginationToken(result.getPaginationToken());
				result = identityProvider.listUsers(listUsersRequest);
				userTypeList = result.getUsers();
				users.addAll(userTypeList.stream().map(u -> convertCognitoUser(u)).collect(Collectors.toList()));
			} catch (TooManyRequestsException e) {
				/** cognito hard rate limit for "list users": 5 per second. */
				try {
					log.warn("Too many requests", e);
					Thread.sleep(200);
				} catch (InterruptedException e1) {
					log.warn("Error while sleeping", e);
				}
			}
		}

		return users;
	}

	protected MyUserModel convertCognitoUser(UserType awsCognitoUser) {

		MyUserModel.MyUserModelBuilder builder = MyUserModel.builder();

		for (AttributeType userAttribute : awsCognitoUser.getAttributes()) {
			switch (userAttribute.getName()) {
			case "sub":
				builder.id(userAttribute.getValue());
				break;
			case "given_name":
				builder.firstname(userAttribute.getValue());
				break;
			}
		}

		return builder.build();
	}
}
