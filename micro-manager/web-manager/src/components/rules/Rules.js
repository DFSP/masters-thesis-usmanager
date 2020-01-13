/*
 * MIT License
 *
 * Copyright (c) 2020 micro-manager
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import React from 'react';
import Utils from '../../utils';
import RuleCard from './RuleCard';
import MainLayout from '../shared/MainLayout';
import { Link } from 'react-router-dom';

export default class Rules extends React.Component {
  constructor (props) {
    super(props);
    this.state = { data: [], loading: false };
  }

  componentDidMount = () => {
    this.loadRules();
  };

  loadRules = () => {
    this.setState({ loading: true });
    Utils.ajaxGet(
      'localhost/rules',
      data => this.setState({ data: data, loading: false })
    );
  };

  render = () => {
    let ruleNodes;
    if (this.state.data) {
      ruleNodes = this.state.data.map(function (rule) {
        return (
          <RuleCard viewDetails={true} key={rule.id} rule={rule}/>
        );
      });
    }
    return (
      <MainLayout title='Rules'>
        <div className="right-align">
          <div className="row">
            <div className="col s12">
              <Link className="waves-effect waves-light btn-small" to='/ui/rules/detail/'>
                New rule
              </Link>
            </div>
          </div>
        </div>
        {ruleNodes}
      </MainLayout>
    );
  };
}