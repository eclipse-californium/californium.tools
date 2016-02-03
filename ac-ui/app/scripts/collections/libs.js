/*global define*/

define([
    'underscore',
    'backbone',
    'models/lib',
    'collections/coap'
], function(_, Backbone, LibModel, CoapCollection) {
    'use strict';

    var LibCollection = CoapCollection.extend({
        model: LibModel,

        defaults: {
            'new': false,
            'script': ''
        },

        url: function() {
            return window.base_url + 'libs';
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

    return LibCollection;
});
