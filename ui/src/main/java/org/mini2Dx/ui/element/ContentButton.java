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
package org.mini2Dx.ui.element;

import java.util.ArrayList;
import java.util.List;

import org.mini2Dx.core.exception.MdxException;
import org.mini2Dx.core.serialization.annotation.ConstructorArg;
import org.mini2Dx.core.serialization.annotation.Field;
import org.mini2Dx.ui.render.ContentButtonRenderNode;
import org.mini2Dx.ui.render.ParentRenderNode;

/**
 * Implementation of {@link Button} that allows custom inner content
 */
public class ContentButton extends Button {
	@Field(optional=true)
	protected final List<UiElement> children = new ArrayList<UiElement>(1);
	
	protected ContentButtonRenderNode renderNode;
	
	/**
	 * Constructor. Generates a unique ID for the {@link ContentButton}
	 */
	public ContentButton() {
		this(null);
	}
	
	/**
	 * Constructor
	 * @param id The unique ID of the {@link ContentButton}
	 */
	public ContentButton(@ConstructorArg(clazz=String.class, name = "id") String id) {
		super(id);
	}
	
	/**
	 * Adds a {@link UiElement} to this {@link ContentButton}
	 * @param element The {@link UiElement} to be added
	 */
	public void add(UiElement element) {
		if(element == null) {
			throw new MdxException("Cannot add null element to ContentButton");
		}
		children.add(element);
		if(renderNode == null) {
			return;
		}
		element.attach(renderNode);
	}
	
	/**
	 * Removes a {@link UiElement} from this {@link ContentButton}
	 * @param element The {@link UiElement} to be removed
	 * @return True if the {@link ContentButton} contained the {@link UiElement}
	 */
	public boolean remove(UiElement element) {
		if(renderNode != null) {
			element.detach(renderNode);
		}
		return children.remove(element);
	}

	@Override
	public void attach(ParentRenderNode<?, ?> parentRenderNode) {
		if(renderNode != null) {
			return;
		}
		renderNode = new ContentButtonRenderNode(parentRenderNode, this);
		for(int i = 0; i < children.size(); i++) {
			children.get(i).attach(renderNode);
		}
		parentRenderNode.addChild(renderNode);
	}

	@Override
	public void detach(ParentRenderNode<?, ?> parentRenderNode) {
		if(renderNode == null) {
			return;
		}
		for (int i = 0; i < children.size(); i++) {
			children.get(i).detach(renderNode);
		}
		parentRenderNode.removeChild(renderNode);
		renderNode = null;
	}
	
	@Override
	public void setVisibility(Visibility visibility) {
		if(this.visibility == visibility) {
			return;
		}
		this.visibility = visibility;
		
		if(renderNode == null) {
			return;
		}
		renderNode.setDirty(true);
	}
	
	@Override
	public void setStyleId(String styleId) {
		if(styleId == null) {
			return;
		}
		if(this.styleId.equals(styleId)) {
			return;
		}
		this.styleId = styleId;
		
		if(renderNode == null) {
			return;
		}
		renderNode.setDirty(true);
	}
	
	@Override
	public void syncWithRenderNode() {
		while(!effects.isEmpty()) {
			renderNode.applyEffect(effects.poll());
		}
	}

	@Override
	public void setZIndex(int zIndex) {
		this.zIndex = zIndex;
		
		if(renderNode == null) {
			return;
		}
		renderNode.setDirty(true);
	}
	
	@Override
	public void setLayout(String layout) {
		if(layout == null) {
			return;
		}
		this.layout = layout;
		if(renderNode == null) {
			return;
		}
		renderNode.setDirty(true);
	}
}
