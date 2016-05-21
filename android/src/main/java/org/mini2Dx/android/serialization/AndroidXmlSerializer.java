/**
 * Copyright (c) 2015 See AUTHORS file
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of the mini2Dx nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.mini2Dx.android.serialization;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.mini2Dx.core.serialization.RequiredFieldException;
import org.mini2Dx.core.serialization.SerializationException;
import org.mini2Dx.core.serialization.XmlSerializer;
import org.mini2Dx.core.serialization.annotation.ConstructorArg;
import org.mini2Dx.core.util.Ref;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.util.Log;
import android.util.Xml;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.reflect.Annotation;
import com.badlogic.gdx.utils.reflect.ArrayReflection;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Field;
import com.badlogic.gdx.utils.reflect.Method;
import com.badlogic.gdx.utils.reflect.ReflectionException;

/**
 * Android implementation of {@link XmlSerializer}
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class AndroidXmlSerializer implements XmlSerializer {

	@Override
	public <T> T fromXml(String xml, Class<T> clazz)
			throws SerializationException {
		return fromXml(new StringReader(xml), clazz);
	}
	
	@Override
	public <T> T fromXml(Reader xmlReader, Class<T> clazz)
			throws SerializationException {
		XmlPullParser xmlParser = Xml.newPullParser();
		T result = null;
		try {
			xmlParser.setInput(xmlReader);
			xmlParser.nextTag();
			xmlParser.nextTag();
			result = deserializeObject(xmlParser, "data", clazz);
		} catch (XmlPullParserException e) {
			throw new SerializationException(e.getMessage(), e);
		} catch (IOException e) {
			throw new SerializationException(e.getMessage(), e);
		} finally {
			try {
				xmlReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	@Override
	public <T> String toXml(T object) throws SerializationException {
		StringWriter writer = new StringWriter();
		toXml(object, writer);
		return writer.toString();
	}
	
	@Override
	public <T> void toXml(T object, Writer writer)
			throws SerializationException {
		org.xmlpull.v1.XmlSerializer xmlSerializer = Xml.newSerializer();
		try {
			xmlSerializer.setOutput(writer);
			xmlSerializer.startDocument("UTF-8", true);
			writeObject(object, object.getClass().getSimpleName(), xmlSerializer);
			xmlSerializer.endDocument();
		} catch (IllegalArgumentException e) {
			throw new SerializationException(e.getMessage(), e);
		} catch (IllegalStateException e) {
			throw new SerializationException(e.getMessage(), e);
		} catch (IOException e) {
			throw new SerializationException(e.getMessage(), e);
		} finally {
			try {
				writer.close();
			} catch (IOException e) {
			}
		}
	}

	private <T> void writeObject(T object, String tagName,
			org.xmlpull.v1.XmlSerializer xmlSerializer)
			throws SerializationException {
		try {
			if(object == null) {
				writePrimitive(tagName, "", xmlSerializer);
				return;
			}

			Class<?> clazz = object.getClass();
			
			if (isPrimitive(clazz) || clazz.equals(String.class)) {
				writePrimitive(tagName, object, xmlSerializer);
				return;
			}
			if (clazz.isEnum()) {
				writePrimitive(tagName, object.toString(), xmlSerializer);
				return;
			}
			if (clazz.isArray()) {
				writeArray(tagName, object, xmlSerializer);
				return;
			}
			if (Collection.class.isAssignableFrom(clazz)) {
				Collection collection = (Collection) object;
				writeArray(tagName, collection.toArray(), xmlSerializer);
				return;
			}
			if (Map.class.isAssignableFrom(clazz)) {
				writeMap(tagName, (Map) object, xmlSerializer);
				return;
			}

			if (tagName != null) {
				xmlSerializer.startTag("", tagName);
			}
			
			Class<?> currentClass = clazz;
			while(currentClass != null && !currentClass.equals(Object.class)) {
				for (Method method : ClassReflection.getDeclaredMethods(currentClass)) {
					if (method.getParameterTypes().length > 0) {
						continue;
					}
					Annotation annotation = method.getDeclaredAnnotation(ConstructorArg.class);
					if (annotation == null) {
						continue;
					}
					ConstructorArg constructorArg = annotation.getAnnotation(ConstructorArg.class);
					xmlSerializer.attribute("", constructorArg.name(), String.valueOf(method.invoke(object)));
				}
				for (Field field : ClassReflection.getDeclaredFields(currentClass)) {
					field.setAccessible(true);
					Annotation annotation = field
							.getDeclaredAnnotation(org.mini2Dx.core.serialization.annotation.Field.class);

					if (annotation == null) {
						continue;
					}
					org.mini2Dx.core.serialization.annotation.Field fieldAnnotation = annotation
							.getAnnotation(org.mini2Dx.core.serialization.annotation.Field.class);

					if (!fieldAnnotation.optional() && field.get(object) == null) {
						throw new RequiredFieldException(currentClass, field.getName());
					}
					writeObject(field.get(object), field.getName(), xmlSerializer);
				}
				currentClass = currentClass.getSuperclass();
			}
			
			if (tagName != null) {
				xmlSerializer.endTag("", tagName);
			}
		} catch (IllegalArgumentException e) {
			throw new SerializationException(e.getMessage(), e);
		} catch (IllegalStateException e) {
			throw new SerializationException(e.getMessage(), e);
		} catch (ReflectionException e) {
			throw new SerializationException(e.getMessage(), e);
		} catch (IOException e) {
			throw new SerializationException(e.getMessage(), e);
		}
	}
	
	private <T> void writeMap(String tagName, Map map, org.xmlpull.v1.XmlSerializer xmlSerializer) throws SerializationException {
		try {
			if (tagName != null) {
				xmlSerializer.startTag("", tagName);
			}
			for (Object key : map.keySet()) {
				xmlSerializer.startTag("", "entry");
				writeObject(key, "key", xmlSerializer);
				writeObject(map.get(key), "value", xmlSerializer);
				xmlSerializer.endTag("", "entry");
			}
			if (tagName != null) {
				xmlSerializer.endTag("", tagName);
			}
		} catch (IllegalArgumentException e) {
			throw new SerializationException(e.getMessage(), e);
		} catch (IllegalStateException e) {
			throw new SerializationException(e.getMessage(), e);
		} catch (IOException e) {
			throw new SerializationException(e.getMessage(), e);
		}
	}
	
	private <T> void writeArray(String tagName, T array, org.xmlpull.v1.XmlSerializer xmlSerializer) throws SerializationException {
		try {
			if (tagName != null) {
				xmlSerializer.startTag("", tagName);
			}

			int arrayLength = Array.getLength(array);
			for (int i = 0; i < arrayLength; i++) {
				writeObject(Array.get(array, i), "value", xmlSerializer);
			}
			
			if (tagName != null) {
				xmlSerializer.endTag("", tagName);
			}
		} catch (IllegalArgumentException e) {
			throw new SerializationException(e.getMessage(), e);
		} catch (IllegalStateException e) {
			throw new SerializationException(e.getMessage(), e);
		} catch (IOException e) {
			throw new SerializationException(e.getMessage(), e);
		}
	}
	
	private <T> void writePrimitive(String tagName, T object, org.xmlpull.v1.XmlSerializer xmlSerializer) throws SerializationException {
		try {
			if (tagName != null) {
				xmlSerializer.startTag("", tagName);
			}
			xmlSerializer.text(String.valueOf(object));
			if (tagName != null) {
				xmlSerializer.endTag("", tagName);
			}
		} catch (IllegalArgumentException e) {
			throw new SerializationException(e.getMessage(), e);
		} catch (IllegalStateException e) {
			throw new SerializationException(e.getMessage(), e);
		} catch (IOException e) {
			throw new SerializationException(e.getMessage(), e);
		}
	}
	
	private <T> T construct(XmlPullParser xmlParser, Ref<Boolean> isEndElement, Class<T> clazz)
			throws InstantiationException, IllegalAccessException, SerializationException, IllegalArgumentException,
			InvocationTargetException, XMLStreamException, XmlPullParserException, IOException {
		Constructor<?>[] constructors = clazz.getConstructors();
		// Single constructor with no args
		if (constructors.length == 1 && constructors[0].getParameterAnnotations().length == 0) {
			isEndElement.set(false);
			return clazz.newInstance();
		}

		Map<String, String> attributes = new HashMap<String, String>();
		for (int i = 0; i < xmlParser.getAttributeCount(); i++) {
			attributes.put(xmlParser.getAttributeName(i).toString(), xmlParser.getAttributeValue(i));
		}
		xmlParser.next();
		isEndElement.set(xmlParser.getEventType() == XmlPullParser.END_TAG);

		Constructor bestMatchedConstructor = null;
		List<ConstructorArg> detectedAnnotations = new ArrayList<ConstructorArg>(1);

		for (int i = 0; i < constructors.length; i++) {
			detectedAnnotations.clear();
			boolean allAnnotated = true;

			for (int j = 0; j < constructors[i].getParameterAnnotations().length; j++) {
				java.lang.annotation.Annotation[] annotations = constructors[i].getParameterAnnotations()[j];
				if (annotations.length == 0) {
					allAnnotated = false;
					break;
				}

				boolean hasConstructorArgAnnotation = false;
				for (int k = 0; k < annotations.length; k++) {
					if (!annotations[i].annotationType().isAssignableFrom(ConstructorArg.class)) {
						continue;
					}
					ConstructorArg constructorArg = (ConstructorArg) annotations[i];
					if (!attributes.containsKey(constructorArg.name())) {
						continue;
					}
					detectedAnnotations.add(constructorArg);
					hasConstructorArgAnnotation = true;
					break;
				}
				if (!hasConstructorArgAnnotation) {
					allAnnotated = false;
				}
			}
			if (!allAnnotated) {
				continue;
			}
			if (detectedAnnotations.size() == attributes.size()) {
				//Found exact match
				bestMatchedConstructor = constructors[i];
				break;
			}
			if (bestMatchedConstructor == null) {
				bestMatchedConstructor = constructors[i];
			} else if (detectedAnnotations.size() > bestMatchedConstructor.getParameterAnnotations().length) {
				bestMatchedConstructor = constructors[i];
			}
		}
		if (bestMatchedConstructor == null) {
			throw new SerializationException("Could not find suitable constructor for class " + clazz.getName());
		}
		if (detectedAnnotations.size() == 0) {
			return clazz.newInstance();
		}

		Object[] constructorParameters = new Object[detectedAnnotations.size()];
		for (int i = 0; i < detectedAnnotations.size(); i++) {
			ConstructorArg constructorArg = detectedAnnotations.get(i);
			constructorParameters[i] = parsePrimitive(attributes.get(constructorArg.name()), constructorArg.clazz());
		}
		return (T) bestMatchedConstructor.newInstance(constructorParameters);
	}

	private <T> T deserializeObject(XmlPullParser xmlParser, String xmlTag, Class<T> clazz)
			throws SerializationException {
		try {
			if (isPrimitive(clazz) || clazz.equals(String.class)) {
				return parsePrimitive(xmlParser.nextText(), clazz);
			}
			if (clazz.isEnum()) {
				return (T) Enum.valueOf((Class<? extends Enum>) clazz,
						xmlParser.nextText());
			}
			
			Ref<Boolean> isEndElement = new Ref<Boolean>();
			T result = construct(xmlParser,isEndElement, clazz);
			if(isEndElement.get()) {
				return result;
			}
			
			int parserEventType = xmlParser.getEventType();
			boolean finished = false;

			while (!finished) {
				switch (parserEventType) {
				case XmlPullParser.START_TAG:
					String currentFieldName = xmlParser.getName();
					Field currentField = findField(clazz, currentFieldName);
					currentField.setAccessible(true);
					
					Class<?> fieldClass = currentField.getType();
					if (fieldClass.isArray()) {
						xmlParser.next();
						setArrayField(xmlParser, currentField, fieldClass,
								result);
						break;
					}
					if (fieldClass.isEnum()) {
						setEnumField(currentField, fieldClass, result, xmlParser.nextText());
						break;
					}
					if (!fieldClass.isPrimitive()) {
						if (fieldClass.equals(String.class)) {
							setPrimitiveField(currentField, fieldClass, result,
									xmlParser.nextText());
						} else if (Map.class.isAssignableFrom(fieldClass)) {
							xmlParser.next();
							setMapField(xmlParser, currentField, fieldClass,
									result);
						} else if (Collection.class.isAssignableFrom(fieldClass)) {
							xmlParser.next();
							setCollectionField(xmlParser, currentField,
									fieldClass, result);
						} else {
							currentField.set(result, deserializeObject(xmlParser, currentFieldName, fieldClass));
						}
						break;
					}
					setPrimitiveField(currentField, fieldClass, result,
							xmlParser.nextText());
					break;
				case XmlPullParser.END_TAG:
					if (xmlParser.getName().equals(xmlTag)) {
						finished = true;
					}
					break;
				case XmlPullParser.END_DOCUMENT:
					finished = true;
					break;
				default:
					break;
				}
				if (!finished) {
					parserEventType = xmlParser.next();
				}
			}
			return result;
		} catch (SerializationException e) {
			throw e;
		} catch (Exception e) {
			throw new SerializationException(e.getMessage(), e);
		}
	}
	
	private Field findField(Class<?> clazz, String fieldName) throws ReflectionException {
		Class<?> currentClass = clazz;
		while(currentClass != null && !currentClass.equals(Object.class)) {
			try {
				Field result = ClassReflection.getDeclaredField(
						currentClass, fieldName);
				if(result == null) {
					continue;
				}
				return result;
			} catch (ReflectionException e) {}
			currentClass = currentClass.getSuperclass();
		}
		throw new ReflectionException("No field '" + fieldName + "' found in class " + clazz.getName());
	}

	private <T> void setCollectionField(XmlPullParser xmlParser, Field field,
			Class<?> fieldClass, T object) throws SerializationException {
		try {
			Collection collection = (Collection) (fieldClass.isInterface() ? new ArrayList()
					: ClassReflection.newInstance(fieldClass));
			Class<?> valueClass = field.getElementType(0);
			
			boolean finished = false;
			while (!finished) {
				switch (xmlParser.getEventType()) {
				case XmlPullParser.START_TAG:
					collection.add(deserializeObject(xmlParser, "value", valueClass));
					break;
				case XmlPullParser.END_TAG:
					if (!xmlParser.getName().equals("value")) {
						finished = true;
					} else {
						xmlParser.next();
					}
					break;
				default:
					xmlParser.next();
					break;
				}
			}
			field.set(object, collection);
		} catch (XmlPullParserException e) {
			throw new SerializationException(e.getMessage(), e);
		} catch (IOException e) {
			throw new SerializationException(e.getMessage(), e);
		} catch (ReflectionException e) {
			throw new SerializationException(e.getMessage(), e);
		}
	}

	private <T> void setMapField(XmlPullParser xmlParser, Field field,
			Class<?> fieldClass, T object) throws SerializationException {
		try {
			Map map = (Map) (fieldClass.isInterface() ? new HashMap()
					: ClassReflection.newInstance(fieldClass));
			Class<?> keyClass = field.getElementType(0);
			Class<?> valueClass = field.getElementType(1);

			boolean finished = false;
			Object key = null;
			Object value = null;
			
			while (!finished) {
				switch (xmlParser.getEventType()) {
				case XmlPullParser.START_TAG: {
					String currentTag = xmlParser.getName();
					switch (currentTag) {
					case "entry":
						xmlParser.nextTag();
						break;
					case "key":
						key = deserializeObject(xmlParser, "key", keyClass);
						break;
					case "value":
						value = deserializeObject(xmlParser, "value", valueClass);
						break;
					default:
						finished = true;
						break;
					}
					break;
				}
				case XmlPullParser.END_TAG: {
					String currentTag = xmlParser.getName();
					switch (currentTag) {
					case "entry":
						xmlParser.nextTag();
						break;
					case "key":
						xmlParser.nextTag();
						break;
					case "value":
						map.put(key, value);
						xmlParser.nextTag();
						break;
					default:
						finished = true;
						break;
					}
					break;
				}
				default:
					// Handle whitespace in pretty XML
					xmlParser.next();
					break;
				}
			}
			field.set(object, map);
		} catch (XmlPullParserException e) {
			throw new SerializationException(e.getMessage(), e);
		} catch (IOException e) {
			throw new SerializationException(e.getMessage(), e);
		} catch (ReflectionException e) {
			throw new SerializationException(e.getMessage(), e);
		}
	}

	private <T> void setArrayField(XmlPullParser xmlParser, Field field,
			Class<?> fieldClass, T object) throws SerializationException {
		try {
			Class<?> arrayType = fieldClass.getComponentType();
			List list = new ArrayList();
			
			boolean finished = false;
			while (!finished) {
				switch (xmlParser.getEventType()) {
				case XmlPullParser.START_TAG: {
					String currentTag = xmlParser.getName();
					if (!currentTag.equals("value")) {
						finished = true;
						break;
					}
					list.add(deserializeObject(xmlParser, "value", arrayType));
					xmlParser.next();
					break;
				}
				case XmlPullParser.END_TAG: {
					String currentTag = xmlParser.getName();
					if (!currentTag.equals("value")) {
						finished = true;
						break;
					} else {
						xmlParser.next();
					}
					break;
				}
				default:
					// Handle whitespace in pretty XML
					xmlParser.next();
					break;
				}
			}
			
			Object targetArray = ArrayReflection.newInstance(arrayType, list.size());
			for (int i = 0; i < list.size(); i++) {
				ArrayReflection.set(targetArray, i, list.get(i));
			}
			field.set(object, targetArray);
		} catch (Exception e) {
			throw new SerializationException(e.getMessage(), e);
		}
	}
	
	private <T> void setEnumField(Field field, Class<?> fieldClass, T object, String value) throws SerializationException {
		try {
			field.set(object, Enum.valueOf((Class<Enum>) fieldClass, value));
		} catch (ReflectionException e) {
			throw new SerializationException(e.getMessage(), e);
		}
	}

	private <T> void setPrimitiveField(Field field, Class<?> fieldClass,
			T object, String value) throws SerializationException {
		try {
			if (fieldClass.equals(Boolean.TYPE)
					|| fieldClass.equals(Boolean.class)) {
				field.set(object, Boolean.parseBoolean(value));
			} else if (fieldClass.equals(Byte.TYPE)
					|| fieldClass.equals(Byte.class)) {
				field.set(object, Byte.parseByte(value));
			} else if (fieldClass.equals(Character.TYPE)
					|| fieldClass.equals(Character.class)) {
				field.set(object, value.charAt(0));
			} else if (fieldClass.equals(Double.TYPE)
					|| fieldClass.equals(Double.class)) {
				field.set(object, Double.parseDouble(value));
			} else if (fieldClass.equals(Float.TYPE)
					|| fieldClass.equals(Float.class)) {
				field.set(object, Float.parseFloat(value));
			} else if (fieldClass.equals(Integer.TYPE)
					|| fieldClass.equals(Integer.class)) {
				field.set(object, Integer.parseInt(value));
			} else if (fieldClass.equals(Long.TYPE)
					|| fieldClass.equals(Long.class)) {
				field.set(object, Long.parseLong(value));
			} else if (fieldClass.equals(Short.TYPE)
					|| fieldClass.equals(Short.class)) {
				field.set(object, Short.parseShort(value));
			} else {
				field.set(object, value);
			}
		} catch (Exception e) {
			throw new SerializationException(e.getMessage(), e);
		}
	}

	private <T> T parsePrimitive(String value, Class<T> fieldClass)
			throws SerializationException {
		try {
			if (fieldClass.equals(Boolean.TYPE)
					|| fieldClass.equals(Boolean.class)) {
				return (T) new Boolean(value);
			} else if (fieldClass.equals(Byte.TYPE)
					|| fieldClass.equals(Byte.class)) {
				return (T) new Byte(value);
			} else if (fieldClass.equals(Character.TYPE)
					|| fieldClass.equals(Character.class)) {
				return (T) ((Character) value.charAt(0));
			} else if (fieldClass.equals(Double.TYPE)
					|| fieldClass.equals(Double.class)) {
				return (T) new Double(value);
			} else if (fieldClass.equals(Float.TYPE)
					|| fieldClass.equals(Float.class)) {
				return (T) new Float(value);
			} else if (fieldClass.equals(Integer.TYPE)
					|| fieldClass.equals(Integer.class)) {
				return (T) new Integer(value);
			} else if (fieldClass.equals(Long.TYPE)
					|| fieldClass.equals(Long.class)) {
				return (T) new Long(value);
			} else if (fieldClass.equals(Short.TYPE)
					|| fieldClass.equals(Short.class)) {
				return (T) new Short(value);
			} else {
				return (T) value;
			}
		} catch (Exception e) {
			throw new SerializationException(e.getMessage(), e);
		}
	}
	
	private boolean isPrimitive(Class<?> clazz) {
		if (clazz.isPrimitive()) {
			return true;
		}
		if (clazz.equals(Boolean.class)) {
			return true;
		}
		if (clazz.equals(Byte.class)) {
			return true;
		}
		if (clazz.equals(Character.class)) {
			return true;
		}
		if (clazz.equals(Double.class)) {
			return true;
		}
		if (clazz.equals(Float.class)) {
			return true;
		}
		if (clazz.equals(Integer.class)) {
			return true;
		}
		if (clazz.equals(Long.class)) {
			return true;
		}
		if (clazz.equals(Short.class)) {
			return true;
		}
		return false;
	}
}
