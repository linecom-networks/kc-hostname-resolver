# Integración aplicación JEE con Red Hat SSO - Resolver

## Historial de cambios

* Version 1.0.1 Primera versión del host-resolver
* Versión 1.0.2 Se actualizan las dependencias, para que incorpore el adapter versión 10.0.2, versión mínima necesaria para que el adapter funcione correctamente en entornos en los que los endpoinds OpenID-connect no se encuentran todos en el mismo servidor RHSSO (por ejemplo casos en que ciertos endpoints están protegidos mediante un API Gateway).


## Objetivos

Describir las adaptaciones a realizar en aplicaciones web desarrolladas en JEE para ser integradas RedHat SSO, y que deban cargar entornos distintos en función de la URL de entrada.

Leer previamente la guía "Integración aplicación JEE con Red Hat SSO"

## Entorno

* Servidor: RedHat SSO 7.3
* Client-Adapter: keycloak 10.0.2
* Servidores aplicaciones: Weblogic 12.2.1, Java 8. 
* Servidores aplicaciones: Weblogic 12.1.2, Java 7. 

## Software requerido

La integración de aplicaciones JEE con RedHat SSO se realiza mediante RedHat Client Adapters. [Documentación oficial Client Adapters](https://access.redhat.com/documentation/en-us/red_hat_single_sign-on/7.3/html/securing_applications_and_services_guide/openid_connect_3#java_adapters)

Para la configuración analizada, Weblogic, el Adapter necesario es el Java Servlet Filter Adapter [Documentación oficial Java Servlet Filter Adapter](https://access.redhat.com/documentation/en-us/red_hat_single_sign-on/7.3/html/securing_applications_and_services_guide/openid_connect_3#servlet_filter_adapter)

## Descripción

Para cumplir con los objetivos descritos, RHSSO / Keycloak proporciona la funcionalidad [Multi-tenancy](https://access.redhat.com/documentation/en-us/red_hat_single_sign-on/7.3/html/securing_applications_and_services_guide/openid_connect_3#multi_tenancy).

Esto implica que debe implementarse la interfaz "org.keycloak.adapters.KeycloakConfigResolver". Para ello Linecom ha desarrollado una implementación que permite distinguir configuraciones en base al *hostname* de la URL solicitado por el usuario en la *request*.

A continuación se describen los pasos para incorporar la funcionalidad *multi-tenancy* a un proyecto JEE existente.

## Configuración Servidor aplicaciones
### Creación fichero de propiedades Resolver

Se creará un fichero de *properties* que vinculará el *hostname* con el fichero de configuración (json) de RHSSO.

Por ejemplo, el fichero *"/path/kc-resolver.properties"*

``` properties
www.dominio.es=/path/external-keycloak.json
internal.dominio.corp=/path/internal-keycloak.json
```

Donde el fichero *external-keycloak.json* contiene el fichero de configuración de RHSSO para usuarios externos, y el fichero *internal-keycloak.json* contiene la configuración de RHSSO para usuarios internos.

### Propiedad entorno resolver

En el arranque del servidor de aplicaciones se debe añadir bajo la propiedad "kc.prop.host.resolver" la ubicación del fichero de properties anteriormente creado.

Ejemplo:

``` properties
set JAVA_PROPERTIES=%JAVA_PROPERTIES% -Dkc.prop.host.resolver=/path/kc-resolver.properties
```


## Configuración proyecto JEE
### Descargar librerías necesarias

Descargar las librerías ubicadas en https://github.com/linecom-networks/kc-hostname-resolver/tree/master/libs al proyecto.


### Configurar descriptor de despliegue

Se deberá añadir al fichero de despligue, *web.xml*, el filtro Java Servlet Filter Adapter, y apuntar éste al Resolver añadido (mediante el parámetro de inicio del filtro denominado "keycloak.config.resolver")

``` xml
<web-app xmlns="http://java.sun.com/xml/ns/javaee"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd"
      version="3.0">

    <module-name>application</module-name>
    <filter>
        <filter-name>Keycloak Filter</filter-name>
        <filter-class>org.keycloak.adapters.servlet.KeycloakOIDCFilter</filter-class>
        <init-param>
            <param-name>keycloak.config.resolver</param-name>
            <param-value>net.linecom.kc.resolver.HostNameBasedKeycloakConfigResolver</param-value>
       </init-param>
    </filter>
    <filter-mapping>
        <filter-name>Keycloak Filter</filter-name>
        <url-pattern>/*</url-pattern>
    </filter-mapping>
    </web-app>
```


