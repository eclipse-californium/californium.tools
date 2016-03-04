/*global define*/

define([
    'jquery',
    'underscore',
    'backbone',
    'typeahead',
    'views/base',
    'templates',
    'models/app_instance'
], function($, _, Backbone, typeahead, BaseView, JST, AppInstance) {
    'use strict';

    var methodClass = {
        'GET': 'btn-success',
        'PUT': 'btn-info',
        'POST': 'btn-primary',
        'DELETE': 'btn-danger',
    };

    function escapeRegExp(str) {
        return str.replace(/[\-\[\]\/\{\}\(\)\*\+\?\.\\\^\$\|]/g, "\\$&");
    }

    var substringMatcher = function(app_name) {
        return function findMatches(q, x, cb) {
            var matches, substringRegex;
            var substrRegex = new RegExp(escapeRegExp(app_name + q), 'i');
            $.get(window.base_url + '.well-known/core', function(data) {
                var matches = [];
                var strs = data.split(',')
                if (q.length == 0 || q == '/')
                    matches.push('/')
                $.each(strs, function(i, str) {
                    var val = str.substring(1, str.indexOf('>'));
                    if (substrRegex.test(val)) {
                        matches.push(val.substring(app_name.length));
                    }
                });
                cb(matches);
            });
        };
    };

    var AppInstanceView = BaseView.extend({
        template: JST['app/scripts/templates/test.ejs'],

        events: {
            'click .method li a': 'select_method',
            'click .send': 'send',
            'submit form': 'send'
        },

        initialize: function() {},

        select_method: function(e) {
            e.preventDefault();
            var method = $(e.target).text().trim();
            this.$('.method .text').text(method);
            this.$('.method button')
                .removeClass('btn-default')
                .removeClass('btn-info')
                .removeClass('btn-success')
                .removeClass('btn-primary')
                .removeClass('btn-danger')
                .addClass(methodClass[method])
        },

        send: function(e) {
            e.preventDefault();
            var self = this;
            $('.path').typeahead('close');
            this.$('.send').button('loading');
            this.$('.result').hide();
            this.$('.status').hide();
            var xhr = $.ajax({
                type: this.$('.method .text').text(),
                url: window.base_url + 'apps/running/' + this.model.get('name') + this.$('.uri .path.tt-input').val(),
                data: this.$('textarea').val()
            });
            xhr.always(function(x, status) {
                self.$('.send').button('reset');
                self.$('.status').show()
                    .removeClass('hidden')
                    .removeClass('label-danger')
                    .removeClass('label-success')
                    .addClass(status == 'error' ? 'label-danger' : 'label-success')
                    .text(xhr.status + " " + xhr.statusText);

                self.$('.result').show().text(xhr.responseText);
            });
        },

        after_render: function() {
            this.$('.path').typeahead({
                hint: true,
                highlight: true,
                minLength: 1
            }, {
                name: 'states',
                source: substringMatcher('/apps/running/' + this.model.get('name'))
            });
            this.$('.twitter-typeahead').css({
                'position': 'relative',
                'display': 'block',
                'height': '32px'
            });
        },


        redirect_to_home: function() {
            window.Router.navigate("/home", true);
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
