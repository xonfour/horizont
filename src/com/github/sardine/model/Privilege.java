package com.github.sardine.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlMixed;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = { "content" })
@XmlRootElement(name = "privilege")
public class Privilege {
	@XmlMixed
	@XmlAnyElement(lax = true)
	private List<Object> content;

	public List<Object> getContent() {
		if (this.content == null) {
			this.content = new ArrayList<Object>();
		}
		return this.content;
	}

	public void setContent(final List<Object> content) {
		this.content = content;
	}

}
