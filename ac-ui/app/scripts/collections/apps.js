/*global define*/

define([
    'underscore',
    'backbone',
    'models/app',
    'collections/coap'
], function(_, Backbone, AppModel, CoapCollection) {
    'use strict';

    var AppCollection = CoapCollection.extend({
        model: AppModel,

        defaults: {
            'new': false,
            'script': ''
        },

        url: function() {
            return window.base_url + 'install';
        },

        parse: function(response) {
            var valid_lines = _.filter(response.split('\n'), function(v) {
                return v;
            });

            function parseLine(v) {
                return {
                    name: v
                };
            }
            return _.map(valid_lines, parseLine);
        }
    });

    return AppCollection;
});
