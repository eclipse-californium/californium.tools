/*global define*/

define([
    'underscore',
    'backbone',
    'models/coap'
], function(_, Backbone, CoapModel) {
    'use strict';

    var LibModel = CoapModel.extend({

        idAttribute: 'name',

        url: function() {
            return window.base_url + 'libs/' + this.get('name');
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

    return LibModel;
});
