// authorize.js
let _isSafari = false;
var ua = navigator.userAgent.toLowerCase();
if (ua.indexOf('safari') != -1) {
  if (ua.indexOf('chrome') > -1) {
  } else if (ua.indexOf('mac') != -1) {
    _isSafari = true;
  }
}

const _serverUrl = _isSafari ? "https://127.0.0.1:31414" : "http://127.0.0.1:31415";
const token = "$token$";
const appKey = "$appKey$";

const header = new Headers({
  'x-circle-appkey': appKey,
  'Authorization': 'Bearer ' + token,
  'Accept': 'application/json',
  'Content-Type': 'application/json; charset=utf-8'
});

var Circle = {

  authorize: async function () {
    const body = {
      Token: token,
    }

    if (token == "" || appKey == "") {
      throw ("no token or appkey");
    }

    return fetch(_serverUrl + '/v1/authorize', {
      method: "POST",
      headers: new Headers({
        'x-circle-appkey': appKey,
        'Accept': 'application/json',
        'Content-Type': 'application/json; charset=utf-8'
      }),
      body: JSON.stringify(body)
    }).then(async res => {
      const data = JSON.parse(await res.text());
      console.log("primeiro token=" + token + " authorize ret=");
      console.log(data);
      return data && data.Status.Result;
    });
  },
  enumCircles: async () => {

    const body = {};

    return fetch(_serverUrl + '/v1/enumCircles', {
      method: "POST",
      headers: header,
      body: JSON.stringify(body)
    }).then(async res => {
      const data = JSON.parse(await res.text());
      return data;
    }).catch(e => {
      console.log("catch from enumCircle");
      console.log(e);
    });

  },
  enumTopics: async function (CircleId) {
    const body = {
      CircleId
    };

    return fetch(_serverUrl + '/v1/enumTopics', {
      method: "POST",
      headers: header,
      body: JSON.stringify(body)
    }).then(async res => {
      const data = JSON.parse(await res.text());
      return data;
    });
  },
  addMessage: async function (CircleId, TopicId, MessageType, MessageSubType, Message,
    Context, ObjectPath, Base64Data, AdditionalJson) {
    const body = {
      CircleId, TopicId, MessageType, MessageSubType, Message, Context, ObjectPath,
      Base64Data, AdditionalJson
    };

    return fetch(_serverUrl + '/v1/addMessage', {
      method: "POST",
      headers: header,
      body: JSON.stringify(body)
    }).then(async res => {
      const data = JSON.parse(await res.text());
      return data;
    });
  },
  getMessages: async function (CircleId, TopicId, MsgTypeFilters) {
    const body = {
      CircleId, TopicId, MsgTypeFilters
    };

    return fetch(_serverUrl + '/v1/getMessages', {
      method: "POST",
      headers: header,
      body: JSON.stringify(body)
    }).then(async res => {
      const data = JSON.parse(await res.text());
      return data;
    });
  }
}

async function getCircleAndTopic() {

  const allCircles = await Circle.enumCircles();
  if (!allCircles || !allCircles.Status.Result || !allCircles.CircleMeta || !allCircles.CircleMeta.length) {
    console.log("Cant retrieve Circle");
    return null;
  }

  const firstCircle = allCircles.CircleMeta[0];

  const allTopics = await Circle.enumTopics(firstCircle.CircleId);
  if (!allTopics || !allTopics.Status.Result || !allTopics.Topics || !allTopics.Topics.length) {
    console.log("Cant retrieve Topic");
    return null;
  }

  const firstTopic = allTopics.Topics[0];
  return {
    CircleId: firstCircle.CircleId,
    TopicId: firstTopic.TopicId,
  };
}

async function getCircleSavedToken(tokenType) {

  const isAuthorizedNode = await Circle.authorize();

  const circleTopicData = await getCircleAndTopic();
  if (!circleTopicData) {
    return "";
  }

  const allMessages = await Circle.getMessages(circleTopicData.CircleId, circleTopicData.TopicId, [100]);
  if (!allMessages || !allMessages.Status.Result || !allMessages.Messages) {
    console.log("Cant retrieve Messages");
    return "";
  }
  for (let index = allMessages.Messages.length - 1; index >= 0; index--) {
    const messageData = allMessages.Messages[index];
    if (messageData.Message && messageData.AdditionalJson && JSON.parse(messageData.AdditionalJson).type === tokenType) {
      return messageData.Message;
    }
  }
  return "";
}


async function saveTokenToCircle(tokenType, tokenData) {

  const circleTopicData = await getCircleAndTopic();
  if (!circleTopicData) {
    return false;
  }
  const addMessage = await Circle.addMessage(circleTopicData.CircleId, circleTopicData.TopicId, //
    100, 20, tokenData, "", "", "", JSON.stringify({ type: tokenType }));
  if (addMessage) return true;
}

async function isAuthorizedNode() {
  const isAuthorizedNode = await Circle.authorize();
  return isAuthorizedNode;
}

async function saveToken(tokenType, tokenData) {
  const isAuthorizedNode = await Circle.authorize();
  const isSaved = await saveTokenToCircle(tokenType, tokenData);
  return isSaved;
}


