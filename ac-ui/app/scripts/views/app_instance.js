/*global define*/

define([
    'jquery',
    'underscore',
    'backbone',
    'views/base',
    'templates',
    'models/app_instance'
], function($, _, Backbone, BaseView, JST, AppInstance) {
    'use strict';

    var AppInstanceView = BaseView.extend({
        template: JST['app/scripts/templates/app_instance.ejs'],

        events: {
            'click #delete': 'delete_app_instance',
            'click .stop': 'stop_app_instance',
            'click .start': 'start_app_instance',
        },

        initialize: function() {},

        delete_app_instance: function(e) {
            var self = this;
            this.$("#delete").button("loading");
            this.model.destroy();
        },

        redirect_to_home: function() {
            window.Router.navigate("/home", true);
        },

        stop_app_instance: function() {
            $.post(this.model.url(), 'stop')
                .done($.proxy(function() {
                    this.model.set('running', 'stop');
                }, this));
        },

        start_app_instance: function() {
            $.post(this.model.url(), 'start')
                .done($.proxy(function() {
                    this.model.set('running', 'start');
                }, this));
        },

        set_attr: function(args) {
            var app = args[0];
            if (this.model)
                this.stopListening(this.model)
            this.model = window.AppInstanceCollection.findWhere({
                name: app
            });
            this.render();
            this.listenTo(this.model, 'change', this.render);
            this.listenTo(this.model, 'remove', this.redirect_to_home);
            this.model.set({
                name: app
            });
            this.model.fetch({
                parse: true
            });
        }
    });

    return AppInstanceView;
});
