package org.fusesource.fabric.apollo.amqp.generator;

import org.fusesource.fabric.apollo.amqp.jaxb.schema.Amqp;
import org.fusesource.fabric.apollo.amqp.jaxb.schema.Definition;
import org.fusesource.fabric.apollo.amqp.jaxb.schema.Section;
import org.fusesource.fabric.apollo.amqp.jaxb.schema.Type;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class XmlDefinitionParser {
    private final Generator generator;

    public XmlDefinitionParser(Generator generator) {
        this.generator = generator;
    }

    void parseXML() throws JAXBException, SAXException, ParserConfigurationException, IOException {
        JAXBContext jc = JAXBContext.newInstance(Amqp.class.getPackage().getName());
        for ( File inputFile : generator.getInputFiles() ) {
            BufferedReader reader = new BufferedReader(new FileReader(inputFile));

            // JAXB has some namespace handling problems:
            Unmarshaller unmarshaller = jc.createUnmarshaller();
            SAXParserFactory parserFactory;
            parserFactory = SAXParserFactory.newInstance();
            parserFactory.setNamespaceAware(false);
            XMLReader xmlreader = parserFactory.newSAXParser().getXMLReader();
            xmlreader.setEntityResolver(new EntityResolver() {
                public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
                    InputSource is = null;
                    if ( systemId != null && systemId.endsWith("amqp.dtd") ) {
                        is = new InputSource();
                        is.setPublicId(publicId);
                        is.setSystemId(Generator.class.getResource("amqp.dtd").toExternalForm());
                    }
                    return is;
                }
            });

            Source er = new SAXSource(xmlreader, new InputSource(reader));

            // Amqp amqp = (Amqp) unmarshaller.unmarshal(new StreamSource(new
            // File(inputFile)), Amqp.class).getValue();
            Amqp amqp = (Amqp) unmarshaller.unmarshal(er);

            // Scan document:
            for ( Object docOrSection : amqp.getDocOrSection() ) {
                if ( docOrSection instanceof Section ) {
                    Section section = (Section) docOrSection;

                    for ( Object docOrDefinitionOrType : section.getDocOrDefinitionOrType() ) {
                        if ( docOrDefinitionOrType instanceof Type ) {
                            Type type = (Type) docOrDefinitionOrType;

                            Log.info("Section : %s - Type name=%s class=%s provides=%s source=%s", section.getName(), type.getName(), type.getClazz(), type.getProvides(), type.getSource());

                            generator.getClasses().add(type.getClazz());
                            generator.getSections().put(type.getName(), section.getName());

                            if ( type.getProvides() != null ) {
                                generator.getProvides().add(type.getProvides());
                            }

                            if ( type.getClazz().startsWith("primitive") ) {
                                generator.getPrimitives().add(type);
                            } else if ( type.getClazz().startsWith("restricted") ) {
                                generator.getRestricted().add(type);
                                generator.getRestrictedMapping().put(type.getName(), type.getSource());
                            } else if ( type.getClazz().startsWith("composite") ) {
                                generator.getCompositeMapping().put(type.getName(), generator.getPackagePrefix() + "." + generator.getTypes() + "." + Utilities.toJavaClassName(type.getName()));
                                generator.getComposites().add(type);
                            }

                        } else if ( docOrDefinitionOrType instanceof Definition ) {

                            Definition def = (Definition) docOrDefinitionOrType;
                            generator.getDefinitions().add(def);
                            //DEFINITIONS.put(def.getName(), new AmqpDefinition(def));
                        }
                    }
                }
            }
            reader.close();
        }
    }
}