package com.intalio.web.profile.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.codehaus.jackson.JsonParseException;
import org.eclipse.bpmn2.Definitions;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.intalio.bpmn2.impl.Bpmn2JsonMarshaller;
import com.intalio.bpmn2.impl.Bpmn2JsonUnmarshaller;
import com.intalio.web.plugin.IDiagramPlugin;
import com.intalio.web.plugin.impl.PluginServiceImpl;
import com.intalio.web.profile.IDiagramProfile;
import java.util.ArrayList;

import org.eclipse.bpmn2.DocumentRoot;
import org.eclipse.bpmn2.Bpmn2Package;
import org.eclipse.bpmn2.util.Bpmn2ResourceFactoryImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;


/**
 * The implementation of the jBPM profile for Process Designer.
 * @author Tihomir Surdilovic
 */
public class KmrProfileImpl implements IDiagramProfile {
    
    private static Logger _logger = LoggerFactory.getLogger(KmrProfileImpl.class);
    
    private Map<String, IDiagramPlugin> _plugins = new LinkedHashMap<String, IDiagramPlugin>();

    private String _stencilSet;
    private String _externalLoadHost;
    private String _externalLoadProtocol;
    private String _externalLoadSubdomain;
    private String _usr;
    private String _pwd;
    
    public KmrProfileImpl(ServletContext servletContext) {
        this(servletContext, true);
    }
    
    public KmrProfileImpl(ServletContext servletContext, boolean initializeLocalPlugins) {
        if (initializeLocalPlugins) {
            initializeLocalPlugins(servletContext);
        }
    }

    public String getTitle() {
        return "KMR Process Designer";
    }

    public String getStencilSet() {
        return _stencilSet;
    }

    public Collection<String> getStencilSetExtensions() {
        return new ArrayList<String>(){{add("extensions/bpmnkmr/bpmnkmr-2.0.json");}};
    }

    public Collection<String> getPlugins() {
        return Collections.unmodifiableCollection(_plugins.keySet());
    }
    
    private void initializeLocalPlugins(ServletContext context) {
        Map<String, IDiagramPlugin> registry = PluginServiceImpl.getLocalPluginsRegistry(context);
        //we read the default.xml file and make sense of it.
        FileInputStream fileStream = null;
        try {
            try {
                fileStream = new FileInputStream(new StringBuilder(context.getRealPath("/")).append("/").
                        append("/").append("profiles").append("/").append("kmr.xml").toString());
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(fileStream);
            while(reader.hasNext()) {
                if (reader.next() == XMLStreamReader.START_ELEMENT) {
                    if ("profile".equals(reader.getLocalName())) {
                        for (int i = 0 ; i < reader.getAttributeCount() ; i++) {
                            if ("stencilset".equals(reader.getAttributeLocalName(i))) {
                                _stencilSet = reader.getAttributeValue(i);
                            }
                        }
                    } else if ("plugin".equals(reader.getLocalName())) {
                        String name = null;
                        for (int i = 0 ; i < reader.getAttributeCount() ; i++) {
                            if ("name".equals(reader.getAttributeLocalName(i))) {
                                name = reader.getAttributeValue(i);
                            }
                        }
                        _plugins.put(name, registry.get(name));
                    } else if ("externalloadurl".equals(reader.getLocalName())) {
                        for (int i = 0 ; i < reader.getAttributeCount() ; i++) {
                            if ("protocol".equals(reader.getAttributeLocalName(i))) {
                                _externalLoadProtocol = reader.getAttributeValue(i);
                            }
                            if ("host".equals(reader.getAttributeLocalName(i))) {
                                _externalLoadHost = reader.getAttributeValue(i);
                            }
                            if ("subdomain".equals(reader.getAttributeLocalName(i))) {
                                _externalLoadSubdomain = reader.getAttributeValue(i);
                            }
                            if ("usr".equals(reader.getAttributeLocalName(i))) {
                                _usr = reader.getAttributeValue(i);
                            }
                            if ("pwd".equals(reader.getAttributeLocalName(i))) {
                                _pwd = reader.getAttributeValue(i);
                            }
                        }
                    }
                }
            }
        } catch (XMLStreamException e) {
            _logger.error(e.getMessage(), e);
            throw new RuntimeException(e); // stop initialization
        } finally {
            if (fileStream != null) { try { fileStream.close(); } catch(IOException e) {}};
        }
    }

    public String getName() {
        return "kmr";
    }

    public String getSerializedModelExtension() {
        return "bpmn";
    }
      
    public String getExternalLoadURLProtocol() {
        return _externalLoadProtocol;
    }

    public String getExternalLoadURLHostname() {
        return _externalLoadHost;
    }

    public String getExternalLoadURLSubdomain() {
        return _externalLoadSubdomain;
    }

    public String getUsr() {
        return _usr;
    }

    public String getPwd() {
        return _pwd;
    }
    
    public IDiagramMarshaller createMarshaller() {
        return new IDiagramMarshaller() {
            public String parseModel(String jsonModel, String preProcessingData) {
                Bpmn2JsonUnmarshaller unmarshaller = new Bpmn2JsonUnmarshaller();
                Definitions def;
                try {
                    def = unmarshaller.unmarshall(jsonModel, preProcessingData);
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    def.eResource().save(outputStream, Collections.singletonMap(XMLResource.OPTION_ENCODING, "UTF-8"));
                    return outputStream.toString();
                } catch (JsonParseException e) {
                    _logger.error(e.getMessage(), e);
                } catch (IOException e) {
                    _logger.error(e.getMessage(), e);
                }

                return "";
            }
        };
    }

    public IDiagramUnmarshaller createUnmarshaller() {
        return new IDiagramUnmarshaller() {
            public String parseModel(String xmlModel, IDiagramProfile profile, String preProcessingData) {
                Bpmn2JsonMarshaller marshaller = new Bpmn2JsonMarshaller();
                marshaller.setProfile(profile);
                try {
                    return marshaller.marshall(getDefinitions(xmlModel), preProcessingData);
                } catch (Exception e) {
                    _logger.error(e.getMessage(), e);
                }
                return "";
            }
        };
    }
    
    private Definitions getDefinitions(String xml) {
        try {
            ResourceSet resourceSet = new ResourceSetImpl();
            resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put(Resource.Factory.Registry.DEFAULT_EXTENSION, new Bpmn2ResourceFactoryImpl());
            resourceSet.getPackageRegistry().put("http://www.omg.org/spec/BPMN/20100524/MODEL", Bpmn2Package.eINSTANCE);
            XMLResource resource = (XMLResource) resourceSet.createResource(URI.createURI("inputStream://dummyUriWithValidSuffix.xml"));
            resource.getDefaultLoadOptions().put(XMLResource.OPTION_ENCODING, "UTF-8");
            resource.setEncoding("UTF-8");
            Map<String, Object> options = new HashMap<String, Object>();
            options.put( XMLResource.OPTION_ENCODING, "UTF-8" );
            InputStream is = new ByteArrayInputStream(xml.getBytes("UTF-8"));
            resource.load(is, options);
            return ((DocumentRoot) resource.getContents().get(0)).getDefinitions();
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    public String getStencilSetURL() {
        return "/designer/stencilsets/bpmn2.0jbpm/bpmn2.0jbpm.json";
    }

    public String getStencilSetNamespaceURL() {
        return "http://b3mn.org/stencilset/bpmn2.0#";
    }

    public String getStencilSetExtensionURL() {
        return "http://oryx-editor.org/stencilsets/extensions/bpmnkmr-2.0#";
    }
}

