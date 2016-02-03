/*global define*/

define([
    'jquery',
    'underscore',
    'backbone',
    'views/base',
    'templates',
    'models/lib',
    '../../bower_components/codemirror/addon/comment/comment',
    '../../bower_components/codemirror/addon/edit/closebrackets',
    '../../bower_components/codemirror/addon/hint/show-hint',
    '../../bower_components/codemirror/addon/lint/lint',
    '../../bower_components/codemirror/mode/javascript/javascript',
    '../../bower_components/codemirror/lib/codemirror'
], function($, _, Backbone, BaseView, JST, Lib, comment, closebrackets, hint, lint, turtle, codemirror) {
    'use strict';

    var LibView = BaseView.extend({
        template: JST['app/scripts/templates/lib.ejs'],

        events: {
            'click #update': 'update',
            'click #save': 'save',
            'click #delete': 'delete_lib'
        },

        set_attr: function(args) {
            var lib_name = args[0];
            if (this.model)
                this.stopListening(this.model)
            if (window.location.hash == '#newlib') {
                this.model = new Lib({
                    'new': true
                });
            } else {
                this.model = window.LibCollection.findWhere({
                    name: lib_name
                });
            }
            this.render();
            this.listenTo(this.model, 'change', this.render);
            this.listenTo(this.model, 'remove', this.redirect_to_home);
            this.model.set({
                name: lib_name,
                script: ''
            });
            this.model.fetch({
                parse: true
            });
        },

        save: function(e) {
            var lib_name = this.$('.lib_name').val().trim();
            $.post(window.base_url + 'libs?' + lib_name, this.editor.getValue())
                .done($.proxy(function() {
                    window.LibCollection.add(new Lib({
                        name: lib_name
                    }));
                    window.Router.navigate("/lib/" + lib_name, true);
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

        delete_lib: function(e) {
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

    return LibView;
});
