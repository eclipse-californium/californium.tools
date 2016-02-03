/*global define*/

define([
    'underscore',
    'backbone'
], function(_, Backbone) {
    'use strict';

    var CoapModel = Backbone.Model.extend({

        fetch: function(options) {
            options = _.extend(options || {}, {
                dataType: 'text'
            });
            Backbone.Model.prototype.fetch.call(this, options);
        },

        destroy: function(options) {
            options = _.extend(options || {}, {
                dataType: 'text'
            });
            Backbone.Model.prototype.destroy.call(this, options);
        }
    });

    return CoapModel;
});
