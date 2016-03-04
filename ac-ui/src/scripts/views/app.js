/*global define*/

define([
    'jquery',
    'underscore',
    'backbone',
    'views/base',
    'templates',
    'models/app',
    'models/app_instance',
    '../../bower_components/codemirror/addon/comment/comment',
    '../../bower_components/codemirror/addon/edit/closebrackets',
    '../../bower_components/codemirror/addon/hint/show-hint',
    '../../bower_components/codemirror/addon/lint/lint',
    '../../bower_components/codemirror/mode/javascript/javascript',
    '../../bower_components/codemirror/lib/codemirror'
], function($, _, Backbone, BaseView, JST, App, AppInstance, comment, closebrackets, hint, lint, turtle, codemirror) {
    'use strict';

    var AppView = BaseView.extend({
        template: JST['app/scripts/templates/app.ejs'],

        events: {
            'click #update': 'update',
            'click #save': 'save',
            'click #delete': 'delete_app',
            'click .start': 'start_instance'
        },

        set_attr: function(args) {
            var app_name = args[0];
            if (this.model)
                this.stopListening(this.model)
            if (window.location.hash == '#new') {
                this.model = new App({
                    'new': true
                });
            } else {
                this.model = window.AppCollection.findWhere({
                    name: app_name
                });
            }
            this.render();
            this.listenTo(this.model, 'change', this.render);
            this.listenTo(this.model, 'remove', this.redirect_to_home);
            this.model.set({
                name: app_name,
                script: ''
            });
            this.model.fetch({
                parse: true
            });
        },

        start_instance: function() {
            var instance_name = this.$('.instance_name').val().trim();
            $.post(this.model.url(), 'name=' + instance_name)
                .done(function() {
                    window.AppInstanceCollection.add(new AppInstance({
                        name: instance_name
                    }));
                    window.Router.navigate("/app_instance/" + instance_name, true);
                }).error(function(data) {
                    alert("Error: " + data.responseText);
                });

        },

        save: function(e) {
            var app_name = this.$('.app_name').val().trim();
            $.post(window.base_url + 'install?' + app_name, this.editor.getValue())
                .done($.proxy(function() {
                    window.AppCollection.add(new App({
                        name: app_name
                    }));
                    window.Router.navigate("/app/" + app_name, true);
                }, this)).error(function(data) {
                    alert("Error: " + data.responseText);
                });
        },

        update: function(e) {
            e.preventDefault();
            $.ajax({
                url: this.model.url(),
                type: 'PUT',
                data: this.editor.getValue()
            }).error(function(data) {
                alert("Error: " + data.responseText);
            });
        },

        delete_app: function(e) {
            var self = this;
            this.$("#delete").button("loading");
            this.model.destroy();
        },

        redirect_to_home: function() {
            window.Router.navigate("/home", true);
        },

        after_render: function() {
            var self = this;
            this.$('textarea').val(this.model.get('script'));
            this.editor = codemirror.fromTextArea(this.$('textarea')[0], {
                lineNumbers: true,
                matchBrackets: true,
                lineNumbers: true,
                autoCloseBrackets: true,
                continueComments: "Enter",
                extraKeys: {
                    "Ctrl-Q": "toggleComment"
                }
            });

            $(window).resize(function() {
                var windowHeight = $(window).height() - 160;
                self.editor.setSize(null, windowHeight);
            }).resize();
        }
    });

    return AppView;
});
