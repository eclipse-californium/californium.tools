/*global define*/

define([
    'jquery',
    'underscore',
    'backbone'
], function($, _, Backbone) {
    'use strict';

    var BaseView = Backbone.View.extend({

        tagName: 'div',

        id: '',
        collection: null,

        events: {},

        subviews: {},

        get_data: function() {

            if (this.model) {
                return this.model.toJSON();
            } else if (this.collection) {
                return this.collection.toJSON();
            } else {
                return {};
            }
        },

        before_render: function() {

        },

        render: function() {
            this.before_render();
            if (this.template) {
                this.$el.html(this.template(this.get_data()));
            }
            _.each(this.subviews, function(v, k) {
                this.$el.find(k).html(v.$el);
                v.render();
            }, this);
            this.after_render();
            this.delegateEvents();
        },

        after_render: function() {

        },

        set_attr: function() {

        },

        dispose: function() {
            _.each(this.subviews, function(v, k) {
                v.dispose();
            }, this);
            this.remove();
            this.off();
            this.model && this.model.off(null, null, this);
            this.collection && !_.isFunction(this.collection) && this.collection.off(null, null, this);

        },

    });

    return BaseView;
});
