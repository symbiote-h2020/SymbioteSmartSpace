/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.h2020.symbiote.ssp.resources.db;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;

import eu.h2020.symbiote.security.accesspolicies.IAccessPolicy;

/**
 *
 * @author Matteo Pardi <m.pardi@nextworks.it>
 */
@Document(collection="policies")
public class AccessPolicy {
    @Id
    private final String id;    
    private final String internalId;
    private final IAccessPolicy policy;
    
    @Field
	@Indexed(name="session_expiration", expireAfterSeconds=DbConstants.EXPIRATION_TIME)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Date session_expiration;
    
    public AccessPolicy() {
        id = "";
        internalId = "";
        policy = null;
    }
    
    public AccessPolicy(String resourceId, String internalId, IAccessPolicy policy) {
        this.id = resourceId;
        this.internalId = internalId;
        this.policy = policy;
    }
    
    public AccessPolicy(String resourceId, String internalId, IAccessPolicy policy, Date currTime) {
        this.id = resourceId;
        this.internalId = internalId;
        this.policy = policy;
        this.session_expiration=currTime;
    }
    
    public String getResourceId() {
        return id;
    }
    
    public String getInternalId() {
        return internalId;
    }
    
    public IAccessPolicy getPolicy() {
        return policy;
    }
    
	public Date getSessionExpiration() {
		return this.session_expiration;
	}

	public void setSessionExpiration(Date session_expiration) {
		this.session_expiration = session_expiration;
	}
}
