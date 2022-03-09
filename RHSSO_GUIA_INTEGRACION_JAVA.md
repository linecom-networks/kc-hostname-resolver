# Integración aplicación JEE con Red Hat SSO

## Objetivos

Describir las adaptaciones a realizar en aplicaciones web desarrolladas en JEE para ser integradas RedHat SSO.

## Entorno

* Servidor RedHat SSO 7.3
* Servidores aplicaciones: Weblogic 12.2.1, Java 8. 
* Servidores aplicaciones: Weblogic 12.1.2, Java 7. 
* JBOSS EAP 7.2

## Software requerido

La integración de aplicaciones JEE con RedHat SSO se realiza mediante RedHat Client Adapters. [Documentación oficial Client Adapters](https://access.redhat.com/documentation/en-us/red_hat_single_sign-on/7.2/html/securing_applications_and_services_guide/openid_connect_3#java_adapters)

Para la configuración analizada, Weblogic, el Adapter necesario es el Java Servlet Filter Adapter [Documentación oficial Java Servlet Filter Adapter](https://access.redhat.com/documentation/en-us/red_hat_single_sign-on/7.2/html/securing_applications_and_services_guide/openid_connect_3#servlet_filter_adapter)

## Descripción

El filtro Java Servlet Adapter de RHSSO (perteneciente a keycloak), intercepta la llamadas validando si se ha recibido el *access_token*; si no es el caso, según su configuración enviará al usuario a la página de inicio de sesión. 

Si se recibe un *access_token*, el filtro validará si éste es valido (firmado por el servidor RHSSO mediante RS256 por defecto, y no caducado), permitiendo o denegando en consecuencia el acceso a los recursos de la aplicación.

El filtro gestionará el refresco del token mediante el *refresh_token* previamente a la caducidad del *access_token*.

En RHSSO, el *access_token* contiene toda la información del *id_token* y además información sobre los roles del usuario sobre los *client_id* (aplicaciones) que tenga asignados.

## Configuración proyecto JEE
### Añadir la librería Java Servlet Filter Adapter al proyecto

Si se utiliza Maven, se añade en pom.xml.


``` xml
<dependency>
    <groupId>org.keycloak</groupId>
    <artifactId>keycloak-servlet-filter-adapter</artifactId>
    <version>10.0.2</version>
</dependency>
```


### Configurar descriptor de despliegue

Se deberá añadir al fichero de despligue, web.xml, el filtro Java Servlet Filter Adapter, y configurar las URLs a proteger.

``` xml
<web-app xmlns="http://java.sun.com/xml/ns/javaee"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd"
      version="3.0">

    <module-name>application</module-name>
    <filter>
        <filter-name>Keycloak Filter</filter-name>
        <filter-class>org.keycloak.adapters.servlet.KeycloakOIDCFilter</filter-class>
    </filter>
    <filter-mapping>
        <filter-name>Keycloak Filter</filter-name>
        <url-pattern>/*</url-pattern>
    </filter-mapping>
    </web-app>
```

En el snippet anterior, el *url pattern* "/\*" marca el *path* que se protegerá.

Si se requiere excluir ciertos *paths* por debajo del *url-pattern* configurado se puede utilizar el parámetro del filtro ```keycloak.config.skipPattern``` para configurar una expresión regular que describa el patrón de exclusión por el que el filtro deberá delegar a la cadena de filtro, es decir dejar continuar la petición. Por defecto no hay *paths* excluídos.

Los patrones son comparados contra la URI de la petición contra el *path* de contexto. Por ejemplo dado el *path* de contexto /app, una petición a */app/index.html* será comparada como */index.html* contra el patrón de exclusión.

Ejemplo de configuración:

```
<init-param>
    <param-name>keycloak.config.skipPattern</param-name>
    <param-value>^/(subpath1|subpath2).*</param-value>
</init-param>
```

Nota: El patrón de exclusión se compara con URI sin el *path* de contexto, y no se tienen en cuenta los parametros que viajan en el *query string*.

### Configuración OIDC

La aplicación deberá incluir dentro del fichero **keycloak.json** la configuración como cliente OIDC del servidor RedHat SSO. Este fichero se almacenará dentro de la carpeta *WEB-INF* de la aplicación a proteger.

El fichero de configuración se deberá configurar con los parámetros indicados por el administrador de RedHat SSO. **El administrador de RedHat SSO, desde la consola de administración, seleccionando la opción de menú "Clients" puede obtener el JSON base de configuración para el cliente.**

A continuación se muestra un ejemplo de configuración:

``` json
{
  "realm" : "linecom",
  "resource" : "MyClientID",  
  "auth-server-url" : "http://keycloak1.linecom.local:8230/auth",
  "public-client": true,
  "principal-attribute": "preferred_username",
  "ssl-required" : "external",  
  "bearer-only" : false  
}
```

Los parámetros más destacados:

| Parámetro | Descripción | Obligatorio | Valores posibles |
|-----------------|------------------|-------------------|------------------------|
| realm | Nombre del realm | SI |
| resource | client_id de la aplicación | SI |
| auth-server-url | URL base del servidor RedHat SSO |SI |
|ssl-required | Determina si toda comunicación hacia y desde RHSSO debe ser sobre HTTPS.| | all, external, none |
| public-client | Si el client es público, en dicho caso no se envían credenciales a RHSSO, por defecto: false |
| principal-attribute | Atributo del token OIDC con el que se alimentará el UserPrincipal, por defecto: sub |
| bearer-only | Debería ser marcado como true para Servicios. Si se activa el adaptador no intentará autenticar usuarios, sino simplemente verificar *bearer* tokens. En caso de aplicaciones con UI de usuario y Servicios puede utilizarse el parámetro *autodetect-bearer-only*, Defecto: false ||true, false |
| expose-token | Permite al navegador del usuario tener acceso al *access token* (via Javascript). Por defecto: false |
| credentials | Credenciales de la aplicación. Expresado en formato *objeto* donde la clave es el tipo de credencial y el valor es el valor del tipo de credencial. Ejemplo:   ```"credentials" : {   "secret" : "11111-222222-333333"   }``` | SI para cliente confidenciales. | password, jwt |
| token-minimum-time-to-live | Cantidad de tiempo, en segundos, para preventivamente refrescar el *access token* activo con RHSSO antes que caduque. Defecto: 0 |



## Recuperación información

### Recuperación usuario autenticado

El ID del usuario autenticado se obtiene desde el Principal, por ejemplo en una página JSP:

``` jsp
<%=request.getUserPrincipal().getName().toString()%>
```

### Acceso al contexto de seguridad

La interfaz ```KeycloakSecurityContext``` permite acceder a los *tokens* directamente. Siendo posible consultar detalles adicionales del token (como datos del perfil de usuario) o bien si se desea invocar a un servicio ReST protegido.

#### Recuperación de datos del usuario autenticado

* Ejemplo: Nombre completo y email del usuario

``` java
Object o = request.getAttribute(KeycloakSecurityContext.class.getName());
if (o!=null && o instanceof KeycloakSecurityContext)
{
    String name = ((KeycloakSecurityContext)o).getToken().getName();
    String email = ((KeycloakSecurityContext)o).getToken().getEmail();
}
```

* Ejemplo: Roles del usuario

Roles del usuario para el *client_id* sobre el que se ha solicitado el token.

``` java
Access aplicacionAccesos = accessToken.getResourceAccess(accessToken.getAudience()[0]);
            
for (String roleUsuario : aplicacionAccesos.getRoles()) {
        System.out.println("Role usuario: " + roleUsuario);
}
```

* Obtención Token para posterior invocación a servicio ReST

```
AccessToken accessToken = ((KeycloakSecurityContext)o).getToken();
```



## Excepciones

El adapter permite excepciones en base al path de la URI tal como se ha explicado anteriormente.

**No se permiten excepciones ni por IP Cliente, ni por parámetros en la URL**

Aquellas aplicaciones que tengan excepciones por IP Origen deberán ser migradas para que los clientes que consumen los Servicios Web, o bien propaguen el token en el caso de que lo tengan, o bien obtengan un token mediante el *flow* [Client Credentials Grant](https://tools.ietf.org/html/rfc6749#section-4.4).

### Caso: Servicio Web y Autenticación consumidores

Dado el siguiente servicio web ReST sobre el *path* /testWS y protegido por el filtro de RHSSO

```
@Path("/testWS")
public class TestRS {   

       @GET    
       public String sayHello(@Context HttpServletRequest hsr) {
          return "{"+
                  "\"msg\": \"Hi "+hsr.getUserPrincipal().getName()+" from Weblogic!\"}"; 
        }
}
```

Un consumidor remoto deberá previamente obtener un *access token* y en la llamada incluir dicho token.

La inclusión de dicho token se realizará en la cabecera "Authorization" con el valor "Bearer " seguido del *access_token*

Ejemplo: 

```
> curl --header "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJZR254SWFMQXlMZVVZcDYzNlRxMDExeUw3X18zMkV3OUtjdnFoRXRIOGRzIn0.eyJqdGkiOiJjZWNjYzM1NS1jZTZlLTQ2NTktYWU1MS1mNDRlOTk1ZGY5MTciLCJleHAiOjE1MjEwNDkxMTgsIm5iZiI6MCwiaWF0IjoxNTIxMDQ4ODE4LCJpc3MiOiJodHRwOi8va2V5Y2xvYWsxLmxpbmVjb20ubG9jYWw6ODIzMC9hdXRoL3JlYWxtcy9saW5lY29tIiwiYXVkIjoiTXlDbGllbnRJRCIsInN1YiI6IjIzMzJlYjJhLWUzNTktNDJiZS1hMjRhLWYzNzU2NDJhY2FjNiIsInR5cCI6IkJlYXJlciIsImF6cCI6Ik15Q2xpZW50SUQiLCJhdXRoX3RpbWUiOjE1MjEwNDU0MjEsInNlc3Npb25fc3RhdGUiOiJlZWFkNThjMC02MzM0LTQ5MTAtOGMxYi01NjkwY2ZjODE3YjkiLCJhY3IiOiIwIiwiYWxsb3dlZC1vcmlnaW5zIjpbIioiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbInVtYV9hdXRob3JpemF0aW9uIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiTXlDbGllbnRJRCI6eyJyb2xlcyI6WyJhZ2VudGUiLCJkaXJlY3RvciJdfSwiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwiY2xpZW50Um9sIjpbImxuYy1jbGllbnRpZGFnZW50ZSIsImxuYy1jbGllbnRpZGRpcmVjdG9yIl0sIm5hbWUiOiJEYXZpZCBDYXNhZG8iLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJkY2FzYWRvIiwiZ2l2ZW5fbmFtZSI6IkRhdmlkIiwiZmFtaWx5X25hbWUiOiJDYXNhZG8iLCJlbWFpbCI6ImRjYXNhZG9AbGluZWNvbS5uZXQifQ.KIEKtEW_uZONBCrkYiY9AV7pQXuHe_vEWF6Q-Yd4lp7thaH5XMn8vN3dX2i50cPA9Iu5n58fLoyo0y23UKnBPDSdmpxV6Pvy400rHa-VTOlo2f6clGB4zVZ9VsnbHO28OqotEGaJ47xDY30_q3kCihKFDjnwyD8YElKtv1lBT5DPMIoxjU9e3vl-DjsOcoC4w44mRfKQzsL3_QzhRfoHzGdOLJ9LlcTmeosFFpaWFtQEAX4278ri0VDIFDoTxoIeE8dbyIRBsCJ--JA6DVRq0kEQ0DH_V75_2yTwR7A3IkJPPYbRJvom3a9xKwVa6GQg5sqQqoejeC7RulR0nhU0CA" http://development08.linecom.local:7001/authfilter/rs/testWS

  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100    36  100    36    0     0     36      0  0:00:01 --:--:--  0:00:01   164{"msg": "Hi dcasado from Weblogic!"}
>
```

