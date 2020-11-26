package hello

import com.slack.api.app_backend.interactive_components.response.Option
import com.slack.api.bolt.App
import com.slack.api.bolt.jetty.SlackAppServer
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import com.slack.api.model.block.Blocks.*
import com.slack.api.model.block.composition.BlockCompositions.markdownText
import com.slack.api.model.block.composition.BlockCompositions.plainText
import com.slack.api.model.block.composition.PlainTextObject
import com.slack.api.model.block.element.BlockElements.asElements
import com.slack.api.model.block.element.BlockElements.button
import com.slack.api.model.block.element.ButtonElement
import com.slack.api.model.kotlin_extension.block.withBlocks
import com.slack.api.model.kotlin_extension.view.blocks
import com.slack.api.model.view.Views.*
import java.net.URLDecoder
import com.slack.api.Slack
import com.slack.api.model.block.Blocks.*
import com.slack.api.methods.kotlin_extension.request.chat.blocks


// export SLACK_SIGNING_SECRET=
// export SLACK_BOT_TOKEN=
// gradle run
fun main() {
  System.setProperty("org.slf4j.simpleLogger.log.com.slack.api", "debug")

  val app = App()

  app.use { req, _, chain ->
    if (req.requestBodyAsString.startsWith("payload=")) {
      val body = req.requestBodyAsString.split("payload=")[1]
      val json = URLDecoder.decode(body, "UTF-8")
      req.context.logger.info(json)
    }
    chain.next(req)
  }

  app.command("/open-modal") { _, ctx ->
    val view = view { v ->
      v.type("modal")
        .callbackId("view-id")
        .title(viewTitle { it.type("plain_text").text("Title") })
        .submit(viewSubmit { it.type("plain_text").text("Submit") })
        .blocks {
          actions {
            blockId("block_1")
            elements {
              externalSelect {
                actionId("select")
                minQueryLength(0)
              }
              button {
                actionId("save")
                text("Save")
                value("1")
              }
            }
          }
          input {
            blockId("block_2")
            label("Plain Text Input")
            element {
              plainTextInput {
                actionId("input")
              }
            }
          }
        }
    }
    val apiResult = ctx.client().viewsOpen { it.triggerId(ctx.triggerId).view(view) }
    if (apiResult.isOk) {
      ctx.ack()
    } else {
      ctx.ackWithJson(apiResult)
    }
  }

  app.command("/message-me") { req, ctx ->
    val channelId = req.payload.channelId

    val response = ctx.client().chatPostMessage { req -> req
        .channel(channelId)
        .blocks {
          section {
            // "text" fields can be constructed via `plainText()` and `markdownText()`
            markdownText("*Please select a restaurant:*")
          }
          divider()
          actions {
            blockId("project-selector")
            elements {
              externalSelect {
                minQueryLength(0)
                actionId("select")
              }

              button {
                actionId("save")
                text("Save")
                value("1")
              }
            }
          }
        }
    }

    if (response.isOk) {
      ctx.ack()
    } else {
      ctx.ackWithJson(response)
    }
  }


  val allOptions = listOf(
      Option(plainText("Schedule"), "schedule"),
      Option(plainText("Budget"), "budget"),
      Option(plainText("Assignment"), "assignment")
  )
  app.blockSuggestion("select") { req, ctx ->
    //    ctx.logger.info("view.state.values: {}", req.payload.view.state.values)
    val keyword = req.payload.value
    if (keyword == null || keyword.isEmpty()) {
      ctx.ack { it.options(allOptions) }
    } else {
      val options = allOptions.filter { (it.text as PlainTextObject).text.contains(keyword) }
      ctx.ack { it.options(options) }
    }
  }

  app.blockAction("save") { req, ctx ->
    ctx.logger.info("view.state.values: {}", req.payload.view.state.values)
    ctx.ack()
  }
  app.blockAction("select") { req, ctx ->
    ctx.logger.info("view.state.values: {}", req.payload.view.state.values)
    ctx.ack()
  }
  app.viewSubmission("view-id") { req, ctx ->
    ctx.logger.info("view.state.values: {}", req.payload.view.state.values)
    ctx.ack()
  }

  val server = SlackAppServer(app)
  server.start()
}