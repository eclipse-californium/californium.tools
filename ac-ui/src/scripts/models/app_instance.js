/*global define*/

define([
    'underscore',
    'backbone',
    'models/coap'
], function(_, Backbone, CoapModel) {
    'use strict';

    var AppInstanceModel = CoapModel.extend({

        idAttribute: 'name',

        defaults: {
            'running': ''
        },

        initialize: function() {
            this.fetch();
            this.listenTo(this.collection, 'updated', this.fetch);
        },

        url: function() {
            return window.base_url + 'apps/instances/' + this.get('name');
        },

        parse: function(response, options) {
            if (typeof response == "string") {
                var valid_lines = _.filter(response.split('\n'), function(v) {
                    return v[0] == '\t';
                });

                function parseLine(v) {
                    var parts = v.split(':');
                    var name = parts[0].substring(1);
                    var value = parts[1].split(' ')[1]
                    return [name, value];
                }
                return _.object(_.map(valid_lines, parseLine));
            }
            return response;
        }
    });

    return AppInstanceModel;
});
