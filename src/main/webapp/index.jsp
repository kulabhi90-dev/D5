<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Personal Details Form</title>
    <style>
        * { box-sizing: border-box; }
        body { font-family: Arial, sans-serif; background: #f4f7f6; margin: 0; min-height: 100vh;
               display: flex; justify-content: center; align-items: center; padding: 16px; }
        .card { background: #fff; padding: 30px; border-radius: 8px; width: 100%; max-width: 420px;
                box-shadow: 0 4px 12px rgba(0,0,0,.1); }
        h2 { margin-top: 0; text-align: center; color: #222; }
        .alert { padding: 12px; margin-bottom: 20px; border-radius: 4px; font-size: 14px; }
        .alert.success { background: #e2f0d9; color: #385723; border: 1px solid #c5e1a5; }
        .alert.error { background: #fdecea; color: #8a1c1c; border: 1px solid #f5c6cb; }
        .field { margin-bottom: 15px; }
        label { display: block; font-weight: bold; margin-bottom: 5px; color: #333; }
        input { width: 100%; padding: 10px; border: 1px solid #ccc; border-radius: 4px; font-size: 15px; }
        input:focus { outline: 2px solid #3498db; border-color: #3498db; }
        button { width: 100%; padding: 12px; border: 0; border-radius: 4px; background: #3498db;
                 color: #fff; font-size: 16px; font-weight: bold; cursor: pointer; }
        button:hover { background: #2980b9; }
    </style>
</head>
<body>
<div class="card">
    <h2>Personal Details</h2>

    <c:if test="${not empty sessionScope.flashMessage}">
        <div class="alert ${sessionScope.flashType}"><c:out value="${sessionScope.flashMessage}"/></div>
        <c:remove var="flashMessage" scope="session"/>
        <c:remove var="flashType" scope="session"/>
    </c:if>

    <form action="${pageContext.request.contextPath}/submit" method="post">
        <div class="field">
            <label for="fullName">Full Name</label>
            <input type="text" id="fullName" name="fullName" maxlength="5" required>
        </div>
        <div class="field">
            <label for="Gender">Gender</label>
            <input type="text" id="fullName" name="fullName" maxlength="5" required>
        </div>
        <div class="field">
            <label for="emailAddress">Email Address</label>
            <input type="email" id="emailAddress" name="emailAddress" maxlength="10" required>
        </div>
        <div class="field">
            <label for="city">City</label>
            <input type="text" id="city" name="city" maxlength="5" required>
        </div>
        <button type="submit">Submit</button>
    </form>
</div>
</body>
</html>
