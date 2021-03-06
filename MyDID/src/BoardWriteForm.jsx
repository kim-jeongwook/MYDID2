import React, { useEffect, useRef, useCallback } from "react";
import { Button, Form } from "react-bootstrap";
import axios from "axios";
axios.defaults.withCredentials = true;
const headers = { withCredentials: true };

function BoardWriteForm(props){
  const imgFile = useRef();
  const boardTitle = useRef();
  const boardContent = useRef();

  const setContentDataCallback = useCallback(() => {
    if (props.location.query !== undefined) {
      boardTitle.current.value = props.location.query.title;
      boardContent.current.value = props.location.query.content;
      // setData(props.location.query.content);
    }
  }, [props.location.query]);

  useEffect(()=>{
    setContentDataCallback();
  },[setContentDataCallback]);

  const writeBoard = () => {
    let url;
    const imgExp = /([^\s]+(?=\.(jpg|gif|png|JPG|GIF|PNG))\.\2)/;

    const formData = new FormData();
    formData.append("headers", headers);
    formData.append("_id", sessionStorage.getItem('login_id'));
    formData.append("title", boardTitle.current.value);
    formData.append("content", boardContent.current.value);

     if (imgFile === undefined || imgFile.current.files.length === 0) {
     
    } else {
      console.log(imgFile);
      formData.append("imgFile", imgFile.current.files[0]);
      formData.append("imgPath", imgFile.current.files[0].name);
    } 

    if (boardTitle.current.value === undefined || boardTitle.current.value === "") {
      alert("글 제목을 입력 해주세요.");
      boardTitle.focus();
      return;
    } else if (boardContent.current.value === undefined || boardContent.current.value === "") {
      alert("글 내용을 입력 해주세요.");
      boardContent.current.focus();
    } else if (imgFile.current.files[0] !== undefined) {
      if (imgFile.current.files[0].name.match(imgExp) === null && imgFile.current.files[0].name !== "") {
        alert("jpg, gif, png 형식의 이미지 파일만 첨부 가능합니다.");
        return;
      }
    }
    
    if (props.location.query !== undefined) {
      formData.append("boardId", props.location.query._id);
      url = process.env.REACT_APP_URL+"/board/update";
    } else {
      url = process.env.REACT_APP_URL+"/board/write";
    }

    axios
      .post(url, formData)
      //정상 수행
      .then(returnData => {
        if(returnData.data.logout){
          logout();
          }else{
          console.log(returnData.data);
          if (returnData.data.message) {
            alert(returnData.data.message);
            window.location.href = "/";
          } else {
            alert("글쓰기 실패");
          }
        }
      })
      //에러
      .catch(err => {
        console.log(err);
      });
  };

  function logout() {
    axios
      .get(process.env.REACT_APP_URL+"/member/logout", { headers })
      .then((returnData) => {
        if (returnData.data.message) {
          sessionStorage.clear();
          alert("로그아웃 되었습니다!");
          window.location.href = "/";
        }
      });
  }

  const divStyle = {
      margin: 50
    };
    const titleStyle = {
      marginBottom: 5
    };
    const buttonStyle = {
      marginTop: 5
    };

    if(props.location.query !== undefined){
    return (
      <div style={divStyle} className="App">
        <h2>글쓰기</h2>
      <Form.Group>
        <Form.Control
          type="text"
          style={titleStyle}
          placeholder="글 제목"
          ref={boardTitle}
        />
        
        <Form.Control ref={boardContent} as="textarea" rows="10" placeholder="글 내용" />
     
       {/*  <CKEditor
          data={data}
          onChange={onEditorChange}
        ></CKEditor> */}
        
        <input type="file" ref={imgFile}></input><br/>
        <Form.Label>※ 이미지 파일을 선택하지 않을 경우 기존 업로드된 이미지 파일을 유지합니다.</Form.Label>
        <Button variant="outline-secondary" style={buttonStyle} onClick={writeBoard} block>
          저장하기
        </Button>
        </Form.Group>
      </div>
    );
  }else{
    return (
      <div style={divStyle} className="App">
        <h2>글쓰기</h2>
      <Form.Group>
        <Form.Control
          type="text"
          style={titleStyle}
          placeholder="글 제목"
          ref={boardTitle}
        />
        
        <Form.Control ref={boardContent} as="textarea" rows="10" placeholder="글 내용" />
     
       {/*  <CKEditor
          data={data}
          onChange={onEditorChange}
        ></CKEditor> */}
        
        <input type="file" ref={imgFile}></input>
        <Button variant="outline-secondary" style={buttonStyle} onClick={writeBoard} block>
          저장하기
        </Button>
        </Form.Group>
      </div>
    );
  }
}

export default BoardWriteForm;
