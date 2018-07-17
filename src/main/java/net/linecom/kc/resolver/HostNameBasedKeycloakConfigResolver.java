/**
 * 
 */
package net.linecom.kc.resolver;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.spi.HttpFacade.Request;




/**
 * @author LNC
 *
 */
public class HostNameBasedKeycloakConfigResolver implements KeycloakConfigResolver {
	
	private final static Logger log = Logger.getLogger("" + HostNameBasedKeycloakConfigResolver.class);
	
	public final static String KC_PROP_HOST_RESOLVER="kc.prop.host.resolver";
	
	private final Map<String, KeycloakDeployment> cache = new ConcurrentHashMap<String, KeycloakDeployment>();	
	private final Properties hostConfigFiles = new Properties();
	
	public KeycloakDeployment resolve(Request request) {

		//1- Initialize hostConfigFiles
		try {

			readHostResolverProperties();
		} catch (Exception ex) {
			log.log(Level.SEVERE, ex.getMessage());
			throw new RuntimeException(ex);
		}
		
		
		//2-Retrieve Hostname and set configFile variable depending on hostname
		URI requestedURI=null;	 
		try {
	 	  requestedURI = new URI(request.getURI());
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	 	String serverName = requestedURI.getHost();
	 	String configFile = matchHostConfigFile(serverName);
	 	
	 	
	 	//3-Finally, set the keycloakdeployment for the Filter. If the keycloak config file isn't loaded, then it'll be loaded and cached.	 
        KeycloakDeployment deployment = cache.get(serverName);
        if (null == deployment) {
        	
        	log.log(Level.INFO, "The specified KeycloakDeployment {0} is not cached. It is going to be created from config file: {1}", new Object[]{serverName, configFile});
        	
        	
            // not found on the simple cache, try to load it from the file system        	
            InputStream is;
            
            try {
            	is = new FileInputStream(configFile);
            	deployment = KeycloakDeploymentBuilder.build(is);
                cache.put(serverName, deployment);
            } catch (Exception ex) {
            	throw new RuntimeException(ex.getMessage());
            }
            
        }
        

        return deployment;
    }
	 
	 /***
	  * El siguiente método inicializa la caché de ficheros de properties asociados a hostnames
	  * El fichero de configuración se obtiene desde la variable de entorno KC_PROP_HOST_RESOLVER
	  * 
	  * El fichero de properties debe ser una fila por entrada, donde cada entrada debe ser:
	  * 
	  * el hostname=path fichero configuración. Por ejemplo:
	  * 
	  *    external.domain.com=/opt/external-keycloak.json
	  *    internal.domain.com=/opt/internal-keycloak.json
	  */
	 public void readHostResolverProperties() throws IOException {
		 if (!hostConfigFiles.isEmpty())
			 return; //already configured
		 
		 String propFilePath = System.getProperty(KC_PROP_HOST_RESOLVER);
		 log.log(Level.INFO, "Initilizing Host Config Files.");
		 
		 if(propFilePath == null || "".equals(propFilePath.trim())) {
			 String message = "Env. variable "+KC_PROP_HOST_RESOLVER+" not defined.";
			 log.log(Level.SEVERE, message);	 
			 throw new RuntimeException(message);
		 }
		 
		 log.log(Level.INFO, KC_PROP_HOST_RESOLVER+": "+propFilePath);
		 
		 InputStream input = null;
		 try {
			 input = new FileInputStream(propFilePath);

			 // load the properties file
			 hostConfigFiles.load(input);
		 }  catch (IOException ex) {
			 	log.log(Level.SEVERE, "Error reading properties file. "+ex.getMessage());	
			 	throw new RuntimeException(ex);				
			} finally {
				if (input != null) {
					try {
						input.close();
					} catch (IOException e) {
						throw new RuntimeException(e);	
					}
				}
			}

		 
		 
		 log.log(Level.INFO, "Host Config Files initialized. #Entries="+hostConfigFiles.size());
	 }
	 
	 /***
	  * Return the keycloak config file path for the specified hostname
	  * 
	  * @param hostname
	  * @return
	  */
	 private String matchHostConfigFile(String hostname) {
		 
		 if (!hostConfigFiles.containsKey(hostname))
		 {
			 throw new IllegalStateException("Not able to determine the config associated to the hostname: "+hostname+" to load the correspondent keycloak.json file");
		 }
		 
		 return hostConfigFiles.getProperty(hostname);
	 }

	

}
