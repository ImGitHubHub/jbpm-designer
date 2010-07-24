/**
 * Copyright (c) 2010
 * Intalio, Inc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
*/

if (!ORYX.Plugins) {
	ORYX.Plugins = {};
}  

if (!ORYX.Config) {
	ORYX.Config = {};
} 

ORYX.Plugins.UUIDRepositorySave = Clazz.extend({
	
    facade: undefined,
	
    construct: function(facade){
	
		this.facade = facade;
		this.facade.offer({
			'name': ORYX.I18N.Save.save,
			'functionality': this.save.bind(this, false),
			'group': ORYX.I18N.Save.group,
			'icon': ORYX.PATH + "images/disk.png",
			'description': ORYX.I18N.Save.saveDesc,
			'index': 1,
			'minShape': 0,
			'maxShape': 0
		});
		
		//capability to set autosave on or off
//		this.facade.offer({
//			'name': ORYX.I18N.Save.autosave,
//			'group': ORYX.I18N.Save.group,
//			'icon': ORYX.PATH + "images/ajax-loader.gif",
//			'description': ORYX.I18N.Save.autosaveDesc,
//			'index': 2,
//			'minShape': 0,
//			'maxShape': 0,
//			'hidden': true
//		});

		// ask before closing the window
		this.changeDifference = 0;		
		this.facade.registerOnEvent(ORYX.CONFIG.EVENT_UNDO_EXECUTE, function(){ this.changeDifference++; });
		this.facade.registerOnEvent(ORYX.CONFIG.EVENT_EXECUTE_COMMANDS, function(){this.changeDifference++; });
		this.facade.registerOnEvent(ORYX.CONFIG.EVENT_UNDO_ROLLBACK, function(){this.changeDifference--; });
		
		window.onbeforeunload = function(){
			if (this.changeDifference > 0){
				return ORYX.I18N.Save.unsavedData;
			}
		}.bind(this);
		
		// let's set autosave on.
//		this.setautosave(this);
	},
	
	/**
	 * Switches autosave on or off.
	 * @param savePlugin the button.
	 */
	setautosave: function(savePlugin) {
		console.log(savePlugin);
		value = !this.autosaving;
		if (value) {
			this.autosaveInternalId = self.setInterval(function() { if (/*savePlugin.changeDifference != 0*/true) { savePlugin._save(savePlugin, true); }}, 10000);
		} else {
			self.clearInterval(this.autosaveInternalId);
		}
		this.autosaving = value;
	},
	
	/**
	 * Saves the current model.
	 */
	save: function(savePlugin) {
		this._save(savePlugin, false);
	},
	
	/**
	 * Saves data by calling the backend.
	 * @param asynchronous whether saving should occur asynchronously
	 */
	_save: function(savePlugin, asynchronous) {
		this.showSaveStatus(savePlugin, asynchronous);
		var svgDOM = DataManager.serialize(this.facade.getCanvas().getSVGRepresentation(true));
		var serializedDOM = Ext.encode(this.facade.getJSON());
		
		// Send the request to the server.
		new Ajax.Request(ORYX.CONFIG.UUID_URL(), {
                method: 'POST',
                asynchronous: asynchronous,
                postBody: Ext.encode({data: serializedDOM, svg : svgDOM, uuid: ORYX.CONFIG.UUID}),
			onSuccess: (function(transport) {
				//show saved status
				this.facade.raiseEvent({
						type:ORYX.CONFIG.EVENT_LOADING_STATUS,
						text:ORYX.I18N.Save.saved
					});
			}).bind(this),
			onFailure: (function(transport) {
				// raise loading disable event.
                this.facade.raiseEvent({
                    type: ORYX.CONFIG.EVENT_LOADING_DISABLE
                });


				Ext.Msg.alert(ORYX.I18N.Oryx.title, ORYX.I18N.Save.failed);
				
				ORYX.log.warn("Saving failed: " + transport.responseText);
			}).bind(this),
			on403: (function(transport) {
				// raise loading disable event.
                this.facade.raiseEvent({
                    type: ORYX.CONFIG.EVENT_LOADING_DISABLE
                });


				Ext.Msg.alert(ORYX.I18N.Oryx.title, ORYX.I18N.Save.noRights);
				
				ORYX.log.warn("Saving failed: " + transport.responseText);
			}).bind(this)
		});
		
		return true;
	},
	
	/**
	 * Shows the saving status
	 * @param asynchronous whether the save is synchronous or asynchronous.
	 */
	showSaveStatus: function(savePlugin, asynchronous) {
		if (asynchronous) {
			console.log("WOIWIOW");
			console.log(savePlugin);
			//show an icon and a message in the toolbar
			savePlugin.hidden = false;
			this.facade.raiseEvent({
	            type: ORYX.CONFIG.EVENT_BUTTON_UPDATE
	        });
		}
	},
	
	/**
	 * Shows the saving status
	 * @param asynchronous whether the save is synchronous or asynchronous.
	 */
	hideSaveStatus: function(asynchronous) {
		if (asynchronous) {
			//show an icon and a message in the toolbar
			this.hidden = true;
			this.facade.raiseEvent({
	            type: ORYX.CONFIG.EVENT_BUTTON_UPDATE
	        });
		}
		
	}
});

/**
 * Method to load model or create new one
 * (moved from editor handler)
 */
window.onOryxResourcesLoaded = function() {
	var stencilset = ORYX.Utils.getParamFromUrl('stencilset') || ORYX.CONFIG.SSET;
	var editor_parameters = {
		id: ORYX.CONFIG.UUID,
		stencilset: {
			url: ORYX.PATH + stencilset
		}
	};
	if(!(ORYX.CONFIG.UUID === undefined)) {
		
 		//load the model from the repository from its uuid
		new Ajax.Request(ORYX.CONFIG.UUID_URL(), {
            asynchronous: false,
            method: 'get',
            onSuccess: function(transport) {
				response = transport.responseText;
				
				if (response.length != 0) {
				    try {
					    model = response.evalJSON();
					    editor_parameters.model = model;
				    } catch(err) {
				    	ORYX.LOG.error(err);
				    }
				}
				
			},
            onFailure: function(transport) {
            	ORYX.LOG.error("Could not load the model for uuid " + ORYX.CONFIG.UUID);
			}
        });
	}
	// finally open the editor:
	new ORYX.Editor(editor_parameters);
};