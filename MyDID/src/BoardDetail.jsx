import React, { useState, useEffect, useRef } from "react";
import { Table, Button, Image ,Form } from "react-bootstrap";
import { NavLink } from "react-router-dom";
import axios from "axios";
import $ from "jquery";
import {} from "jquery.cookie";
axios.defaults.withCredentials = true;
const headers = { withCredentials: true };
var update = true;
function CommentRow(props){
  return (
    <tr>
      <td>
        <NavLink
          to={{ pathname: "/board/detail", query: { _id: props._id } }}
        >
          {props.createdAt.substring(0, 10)}asdf
        </NavLink>
      </td>
      <td>
        <NavLink
          to={{ pathname: "/board/detail", query: { _id: props._id } }}
        >
          {props.title}asdf
        </NavLink>
      </td>
    </tr>
  );
}

function BoardDetail(props){
  const boardTitle = useRef();
  const [board, setBoard] = useState();
  const [commentList, setCommentList] = useState();
  const [cmt, setCmt] = useState();

  useEffect(()=>{
    setBoardDetail();
    update=false;
  },[]);

  const setBoardDetail=()=>{
    if (props.location.query !== undefined) {
      getDetail();
      // getCommentList();
    } else {
      window.location.href = "/";
    }
  };

  const deleteBoard = _id => {
    const send_param = {
      headers,
      _id
    };
    if (window.confirm("정말 삭제하시겠습니까?")) {
      axios
        .post("http://localhost:8080/board/delete", send_param)
        //정상 수행
        .then(returnData => {
          alert("게시글이 삭제 되었습니다.");
          window.location.href = "/";
        })
        //에러
        .catch(err => {
          console.log(err);
          alert("글 삭제 실패");
        });
    }
  };

  const writeComment = () => {
    update = true;
    let url;
    const formData = new FormData();
    const send_param = {
      headers,
      _id: props.location.query._id,
      _comment: boardTitle.current.value
      
    };

    axios
      .post("http://localhost:8080/board/writecomment", send_param)
  }

  const getCommentList = () => {
    console.log(cmt);
    console.log(commentList);
    axios
      .post("http://localhost:8080/board/detail","")
      .then(async returnData => {
        console.log(returnData);
        let commentContents= {};
       
        if (returnData.data.list.length > 0) {
          console.log(returnData.data.list.length);
          const comment = returnData.data.list;
           commentContents = await comment.map(item => (
            <CommentRow
              key={Date.now() + Math.random() * 500}
              _id={item._id}
              createdAt={item.createdAt}
              comment={item.comment}
            ></CommentRow>
          ));
          // console.log(boardList);
        }
        setCommentList(commentContents);
        console.log('hi')
      })
      .catch(err => {
        
        console.log(err);
      });
      console.log('hi2')

  };

  const getDetail = () => {
    const send_param = {
      headers,
      _id: props.location.query._id
    };
    const marginBottom = {
      marginBottom: 5
    };
    const titleStyle = {
      marginBottom: 5
    };


    axios
      .post("http://localhost:8080/board/detail", send_param)
      //정상 수행
      .then(async returnData => {
        if (returnData.data.board[0]) {

          if (returnData.data.list.length > 0) {
            console.log(returnData.data.list.length);
            const comment = returnData.data.list;
            const commentContents = await comment.map(item => (
              <CommentRow
                key={Date.now() + Math.random() * 500}
                _id={item._id}
                createdAt={item.createdAt}
                comment={item.comment}
              ></CommentRow>
            ));
            // console.log(boardList);
            
            console.log(setCommentList(commentContents));
            setCommentList(commentContents);
            console.log('commentList:'+commentList);
            setCmt("ddddd");
            console.log('cmt:'  +cmt);
          }

          const board = (
            <div>
              {/* <Image src={process.env.REACT_APP_URL + returnData.data.board[0].imgPath} fluid /> */}
              <Table striped bordered hover>
                <thead>
                  <tr>
                    <th>{returnData.data.board[0].title}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>{returnData.data.board[0].content}</td>
                  </tr>
                </tbody>
              </Table>
              <div>
                <NavLink
                  to={{
                    pathname: "/boardWrite",
                    query: {
                      title: returnData.data.board[0].title,
                      content: returnData.data.board[0].content,
                      _id: props.location.query._id
                    }
                  }}
                >
                  <Button block style={marginBottom}>
                    글 수정
                  </Button>
                </NavLink>
                <Button
                  block
                  onClick={deleteBoard.bind(
                    null,
                    props.location.query._id
                  )}
                >
                  글 삭제
                </Button>
               
               <br></br>


                      <Form.Control
                type="text"
                style={titleStyle}
                placeholder="댓글쓰기"
                ref={boardTitle}
              />

                <Button
                  block
                  onClick={writeComment}
                >
                  댓글작성 
                </Button>
                <div style={divStyle}>
                    <Table striped bordered hover>

                        <thead>
                          <tr>
                          <th>댓글작성자</th>
                          <th>댓글내용</th>
                          
                          </tr>
                        </thead>
                        <tbody>{commentList}</tbody>
                      </Table>
                </div>
              </div>
            </div>
          );
          setBoard(board);
        } else {
          alert("글 상세 조회 실패");
        }
      })
      //에러
      .catch(err => {
        console.log(err);
      });
  };

  //onClick={this.getBoard.bind(null,this.props._id)}
    const divStyle = {
      margin: 50
    };
    return <div style={divStyle}>{board}</div>;
  
}

export default BoardDetail;
