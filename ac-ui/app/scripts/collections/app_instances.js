/*global define*/

define([
    'underscore',
    'backbone',
    'models/app_instance',
    'collections/coap'
], function(_, Backbone, AppInstanceModel, CoapCollection) {
    'use strict';

    var AppInstanceCollection = CoapCollection.extend({
        model: AppInstanceModel,


        url: function() {
            return window.base_url + 'apps/instances';
        },

        parse: function(response) {
            var valid_lines = _.filter(response.split('\n'), function(v) {
                return v[0] == '\t';
            });

            function parseLine(v) {
                return {
                    name: v.split(':')[0].substring(1)
                };
            }
            return _.map(valid_lines, parseLine);
        }
    });

    return AppInstanceCollection;
});
