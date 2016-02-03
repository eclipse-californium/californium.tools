/*global define*/

define([
    'underscore',
    'backbone',
    'collections/coap'
], function(_, Backbone) {
    'use strict';

    var CoapCollection = Backbone.Collection.extend({

        fetch: function(options) {
            options = _.extend(options || {}, {
                dataType: 'text'
            });
            Backbone.Collection.prototype.fetch.call(this, options);
        }

    });

    return CoapCollection;
});
