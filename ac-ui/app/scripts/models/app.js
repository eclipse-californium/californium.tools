/*global define*/

define([
    'underscore',
    'backbone',
    'models/coap'
], function(_, Backbone, CoapModel) {
    'use strict';

    var AppModel = CoapModel.extend({

        idAttribute: 'name',

        url: function() {
            return window.base_url + 'install/' + this.get('name');
        },

        parse: function(response, options) {
            if (typeof response == "string") {
                return {
                    name: this.get('name'),
                    script: response
                };
            }
            return response;
        }
    });

    return AppModel;
});
