/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ratpack.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.google.common.reflect.TypeToken
import ratpack.http.client.RequestSpec
import ratpack.test.internal.RatpackGroovyDslSpec
import spock.lang.Unroll

import static Jackson.jsonNode

class JacksonParsingSpec extends RatpackGroovyDslSpec {

  def setup() {
    modules << new JacksonModule()
  }

  def "can parse json node"() {
    when:
    handlers {
      post {
        def node = parse jsonNode()
        response.send node.get("value").toString()
      }
    }

    and:
    requestSpec { RequestSpec requestSpec ->
      requestSpec.body.stream({ it << /{"value": 3}/ })
      requestSpec.body.type("application/json")
    }

    then:
    postText() == "3"
  }

  @Unroll
  def "can parse #requestBody with #jsonParserFeature enabled"() {
    given:
    modules.first().configure { config ->
      config.withMapper { objectMapper ->
        objectMapper.configure(jsonParserFeature, true)
      }
    }

    and:
    handlers {
      post {
        def node = parse jsonNode()
        response.send node.get("value").toString()
      }
    }

    and:
    requestSpec { RequestSpec requestSpec ->
      requestSpec.body.stream({ it << requestBody })
      requestSpec.body.type("application/json")
    }

    expect:
    postText() == "3"

    where:
    jsonParserFeature                             | requestBody
    JsonParser.Feature.ALLOW_SINGLE_QUOTES        | /{'value': 3}/
    JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES | /{value: 3}/
  }

  static class Pogo {
    String value
    def foo = [:]
  }

  @Unroll("can parse json as #classType")
  def "can parse json as object"() {
    when:
    Class returnedType

    handlers {
      post {
        def object = parse classType
        returnedType = object.getClass()
        render "${object.value}:${object.foo?.value}"
      }
    }

    and:
    requestSpec { RequestSpec requestSpec ->
      requestSpec.body.type("application/json")
      requestSpec.body.stream({ it << /{"value": 1, "foo": {"value": 2}}/ })
    }

    then:
    postText() == "1:2"
    classType.isAssignableFrom(returnedType)

    where:
    classType << [HashMap, Map, Object, Pogo, JsonNode]
  }

  def "can parse generic type"() {
    when:
    handlers {
      post {
        def body = parse(new TypeToken<List<Integer>>() {})
        render body.collect { it.getClass().name }.toString()
      }
    }

    and:
    requestSpec { RequestSpec requestSpec ->
      requestSpec.body.type("application/json")
      requestSpec.body.stream({ it << /[1]/ })
    }

    then:
    postText() == "[java.lang.Integer]"
  }

}
